package com.linetrans.app.ai

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * 离线本地词库（英汉）。
 *
 * 数据来源：
 *  - 内置 `assets/dict/core.tsv`（常用词，随应用打包）
 *  - 用户导入的 `filesDir/dict-import.tsv`（可换成更大的词典，导入后立刻生效）
 *
 * 每行格式：`单词<TAB>音标<TAB>中文释义`（音标可留空）。
 * 查询命中即返回，不联网，所以划词是秒出。
 */
object LocalDictionary {

    data class Item(val word: String, val phonetic: String, val meaning: String)

    private const val ASSET_PATH = "dict/core.tsv"
    private const val LEMMA_PATH = "dict/lemma.tsv"
    private const val IMPORT_FILE = "dict-import.tsv"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val map = HashMap<String, Item>(40000)
    /** 词形还原：ran → run，用于查不到原形时回退 */
    private val lemmas = HashMap<String, String>(90000)

    @Volatile
    private var loaded = false

    @Volatile
    private var appContext: Context? = null

    val size: Int get() = map.size

    val isReady: Boolean get() = loaded

    /** 应用启动时调用；重复调用无副作用。 */
    fun init(context: Context) {
        appContext = context.applicationContext
        if (loaded) return
        scope.launch { ensureLoaded() }
    }

    suspend fun ensureLoaded(): Boolean {
        val context = appContext ?: return false
        return ensureLoaded(context)
    }

    suspend fun ensureLoaded(context: Context): Boolean {
        if (loaded) return true
        mutex.withLock {
            if (loaded) return true
            val target = HashMap<String, Item>(40000)
            val lemmaTarget = HashMap<String, String>(90000)
            runCatching {
                context.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
                    lines.forEach { parseLine(it, target) }
                }
            }
            runCatching {
                context.assets.open(LEMMA_PATH).bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isBlank() || line[0] == '#') return@forEach
                        val parts = line.split('\t')
                        if (parts.size >= 2) {
                            val form = parts[0].trim().lowercase()
                            val base = parts[1].trim().lowercase()
                            if (form.isNotEmpty() && base.isNotEmpty()) lemmaTarget[form] = base
                        }
                    }
                }
            }
            runCatching {
                val file = File(context.filesDir, IMPORT_FILE)
                if (file.exists()) {
                    file.bufferedReader().useLines { lines -> lines.forEach { parseLine(it, target) } }
                }
            }
            map.clear()
            map.putAll(target)
            lemmas.clear()
            lemmas.putAll(lemmaTarget)
            loaded = true
            return true
        }
    }

    private fun parseLine(line: String, target: HashMap<String, Item>) {
        if (line.isEmpty() || line[0] == '#') return
        val parts = line.split('\t')
        if (parts.size < 2) return
        val word = parts[0].trim().lowercase()
        if (word.isEmpty()) return
        val (phonetic, meaning) = if (parts.size >= 3) {
            parts[1].trim() to parts.drop(2).joinToString(" ").trim()
        } else {
            "" to parts[1].trim()
        }
        if (meaning.isEmpty()) return
        // 用户导入的词典覆盖内置条目
        target[word] = Item(word, phonetic, meaning)
    }

    /** 查词：直接命中 + 常见词形变化。 */
    fun lookup(raw: String): Item? {
        if (!loaded) return null
        val word = raw.trim().trim { !it.isLetter() && it != '-' && it != '\'' }.lowercase()
        if (word.isEmpty()) return null
        map[word]?.let { return it }
        lemmas[word]?.let { base -> map[base]?.let { return it } }
        for (variant in variants(word)) {
            map[variant]?.let { return it }
            lemmas[variant]?.let { base -> map[base]?.let { return it } }
        }
        return null
    }

    private fun variants(word: String): List<String> {
        val out = ArrayList<String>(8)
        fun add(w: String) { if (w.length >= 2 && w != word) out.add(w) }
        if (word.endsWith("ies") && word.length > 4) add(word.dropLast(3) + "y")
        if (word.endsWith("es") && word.length > 3) add(word.dropLast(2))
        if (word.endsWith("s") && word.length > 2) add(word.dropLast(1))
        if (word.endsWith("ing") && word.length > 5) {
            add(word.dropLast(3))
            add(word.dropLast(3) + "e")
        }
        if (word.endsWith("ed") && word.length > 4) {
            add(word.dropLast(2))
            add(word.dropLast(1))
        }
        if (word.endsWith("er") && word.length > 4) {
            add(word.dropLast(2))
            add(word.dropLast(1))
        }
        if (word.endsWith("est") && word.length > 5) add(word.dropLast(3))
        if (word.endsWith("ly") && word.length > 4) add(word.dropLast(2))
        return out
    }

    /** 导入外部词典文件（每行 word TAB [音标 TAB] 释义），成功后立即重新加载。 */
    fun importFrom(context: Context, text: String): Int =
        writeImport(context, text.lineSequence())

    /**
     * 从文件导入词典（流式读取，支持几十万行的词典文件）。
     * 支持两种格式：
     *  1. 制表符：`单词[TAB]音标[TAB]释义` 或 `单词[TAB]释义`
     *  2. ECDICT 的 CSV：`word,phonetic,definition,translation,...`
     */
    fun importFromUri(context: Context, uri: android.net.Uri): Int {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取文件")
        val count = input.bufferedReader().use { reader -> writeImport(context, reader.lineSequence()) }
        return count
    }

    private fun writeImport(context: Context, lines: Sequence<String>): Int {
        val target = File(context.filesDir, IMPORT_FILE)
        var count = 0
        target.outputStream().bufferedWriter().use { writer ->
            lines.forEach { raw ->
                val parsed = parseImportLine(raw) ?: return@forEach
                writer.append(parsed).append('\n')
                count++
            }
        }
        if (count == 0) {
            target.delete()
            return 0
        }
        reload(context)
        return count
    }

    /** 解析一行导入数据，返回规范化的 `词\t音标\t释义`；不合格返回 null。 */
    private fun parseImportLine(raw: String): String? {
        val line = raw.trimEnd()
        if (line.isBlank() || line.startsWith("#")) return null
        var word = ""
        var phonetic = ""
        var meaning = ""
        if (line.contains('\t')) {
            val parts = line.split('\t').map { it.trim() }
            if (parts.size < 2) return null
            word = parts[0]
            if (parts.size >= 3) {
                phonetic = parts[1]
                meaning = parts.drop(2).joinToString(" ")
            } else {
                meaning = parts[1]
            }
        } else if (line.contains(',')) {
            val parts = com.linetrans.app.util.TextParser.splitCsvLine(line).map { it.trim().trim('"') }
            if (parts.size < 4) return null
            word = parts[0]
            phonetic = parts[1]
            meaning = parts[3].ifBlank { parts[2] }
        } else {
            return null
        }
        if (word.isBlank() || meaning.isBlank()) return null
        // 只保留带中文的释义，避免把纯英文释义也塞进来
        if (meaning.none { it.code in 0x4E00..0x9FFF }) return null
        val normalizedMeaning = meaning.replace("\\n", "；").replace("\n", "；").trim()
        return word.trim().lowercase() + "\t" + phonetic.trim() + "\t" + normalizedMeaning
    }

    fun clearImport(context: Context) {
        File(context.filesDir, IMPORT_FILE).delete()
        reload(context)
    }

    fun hasImport(context: Context): Boolean = File(context.filesDir, IMPORT_FILE).exists()

    private fun reload(context: Context) {
        loaded = false
        map.clear()
        appContext = context.applicationContext
        scope.launch { ensureLoaded() }
    }
}
