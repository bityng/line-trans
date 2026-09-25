package com.linetrans.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** 一条自定义词库记录。 */
data class WordEntry(
    var term: String,
    var reading: String = "",
    var meaning: String = "",
    var note: String = "",
    var updatedAt: Long = System.currentTimeMillis()
)

/**
 * 自定义词库。划词查义时优先命中这里，也可以把查到的释义一键存进来。
 * 数据保存在应用私有目录的 wordbook.json，写入做防抖并放在 IO 线程。
 */
object WordbookRepository {
    private const val FILE_NAME = "wordbook.json"
    private const val DEBOUNCE_MS = 500L

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var job: Job? = null
    private var file: File? = null

    val words = mutableStateListOf<WordEntry>()

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, FILE_NAME)
        file = f
        val loaded = runCatching {
            if (!f.exists()) emptyList()
            else gson.fromJson<List<WordEntry>>(f.readText(), object : TypeToken<List<WordEntry>>() {}.type)
                ?: emptyList()
        }.getOrDefault(emptyList())
        words.clear()
        words.addAll(loaded.filter { it.term.isNotBlank() }.sortedBy { it.term.lowercase() })
    }

    fun find(term: String): WordEntry? {
        val key = term.trim().lowercase()
        if (key.isEmpty()) return null
        return words.firstOrNull { it.term.trim().lowercase() == key }
    }

    fun contains(term: String): Boolean = find(term) != null

    /** 新增或覆盖一条词条（按 term 去重）。 */
    fun upsert(term: String, meaning: String, reading: String = "", note: String = "") {
        val key = term.trim()
        if (key.isEmpty()) return
        val existing = find(key)
        if (existing != null) {
            if (reading.isNotBlank()) existing.reading = reading
            if (meaning.isNotBlank()) existing.meaning = meaning
            if (note.isNotBlank()) existing.note = note
            existing.updatedAt = System.currentTimeMillis()
            resort()
        } else {
            words.add(WordEntry(key, reading, meaning, note))
            resort()
        }
        schedule()
    }

    fun remove(term: String) {
        words.removeAll { it.term.trim().lowercase() == term.trim().lowercase() }
        schedule()
    }

    fun clear() {
        words.clear()
        schedule()
    }

    fun count(): Int = words.size

    private fun resort() {
        val sorted = words.sortedBy { it.term.lowercase() }
        words.clear()
        words.addAll(sorted)
    }

    private fun schedule() {
        synchronized(lock) {
            job?.cancel()
            job = scope.launch {
                delay(DEBOUNCE_MS)
                writeNow()
            }
        }
    }

    fun flush() {
        synchronized(lock) { job?.cancel() }
        scope.launch { writeNow() }
    }

    private fun writeNow() {
        val f = file ?: return
        val payload = runCatching { gson.toJson(words.toList()) }.getOrNull() ?: return
        runCatching { f.writeText(payload) }
    }

    // ---------- 文本导入 / 导出 ----------

    /**
     * 从文本导入，每行一条：
     *   词=释义
     *   词=音标=释义        （两个等号时中间是音标）
     *   词<TAB>释义
     * 以 # 开头的行视为注释。
     */
    fun importText(text: String): Int {
        var added = 0
        text.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val parts = when {
                line.contains("\t") -> line.split("\t").map { it.trim() }.filter { it.isNotEmpty() }
                line.contains("=") -> line.split("=").map { it.trim() }
                else -> return@forEach
            }
            if (parts.size < 2) return@forEach
            val term = parts[0]
            if (term.isBlank()) return@forEach
            val reading = if (parts.size >= 3) parts[1] else ""
            val meaning = if (parts.size >= 3) parts.drop(2).joinToString(" = ") else parts.drop(1).joinToString(" = ")
            if (meaning.isBlank()) return@forEach
            upsert(term, meaning, reading)
            added++
        }
        return added
    }

    fun exportText(): String = words.joinToString("\n") { w ->
        if (w.reading.isBlank()) w.term + "=" + w.meaning
        else w.term + "=" + w.reading + "=" + w.meaning
    }
}
