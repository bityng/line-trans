package com.linetrans.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.linetrans.app.model.TranslationDoc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** 文档排序方式。 */
enum class DocSort(val label: String) {
    UPDATED("最近更新"),
    NAME("名称"),
    PROGRESS("进度")
}

/**
 * 文档仓库。内存里保存全部文档，磁盘写入做防抖并放到 IO 线程，
 * 避免打字时每个字符都同步写文件卡住输入。
 */
object DocRepository {
    private const val DEFAULT_DEBOUNCE_MS = 700L

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    /** 待写入磁盘的内容（id -> json），由 [lock] 保护。 */
    private val pending = LinkedHashMap<String, String>()
    private val jobs = HashMap<String, Job>()

    private var dir: File? = null

    var sortMode: DocSort = DocSort.UPDATED

    val docs = mutableStateListOf<TranslationDoc>()

    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.filesDir, "docs").apply { mkdirs() }
        loadAll()
    }

    private fun loadAll() {
        val loaded = dir?.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { f ->
                runCatching {
                    val doc = gson.fromJson(f.readText(), TranslationDoc::class.java)
                    // 防御 Gson 反序列化出的空字段
                    if (doc == null || doc.id.isBlank()) null else doc.also { it.units.size }
                }.getOrNull()
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
        docs.clear()
        docs.addAll(loaded)
    }

    private fun debounceMs(): Long =
        runCatching { SettingsRepository.settings.autoSaveMs.toLong() }
            .getOrDefault(DEFAULT_DEBOUNCE_MS)
            .coerceIn(200L, 5000L)

    /** 保存到内存并（防抖）写入磁盘；[immediate] 用于离开编辑页等需要立刻落盘的场景。 */
    fun save(doc: TranslationDoc, immediate: Boolean = false) {
        doc.updatedAt = System.currentTimeMillis()
        val idx = docs.indexOfFirst { it.id == doc.id }
        if (idx >= 0) {
            if (docs[idx] !== doc) docs[idx] = doc
        } else {
            docs.add(doc)
            sort()
        }

        val payload = runCatching { gson.toJson(doc) }.getOrNull() ?: return
        synchronized(lock) {
            pending[doc.id] = payload
            jobs.remove(doc.id)?.cancel()
            if (!immediate) {
                jobs[doc.id] = scope.launch {
                    delay(debounceMs())
                    writePending(doc.id)
                }
            }
        }
        if (immediate) writePending(doc.id)
    }

    /** 立刻把还没有落盘的内容写入磁盘。 */
    fun flushAll() {
        val ids = synchronized(lock) { pending.keys.toList() }
        if (ids.isEmpty()) return
        synchronized(lock) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
        scope.launch { ids.forEach { writePending(it) } }
    }

    private fun writePending(id: String) {
        val payload = synchronized(lock) {
            jobs.remove(id)?.cancel()
            pending.remove(id)
        } ?: return
        val d = dir ?: return
        runCatching { File(d, "$id.json").writeText(payload) }
    }

    fun delete(id: String) {
        synchronized(lock) {
            pending.remove(id)
            jobs.remove(id)?.cancel()
        }
        File(dir, "$id.json").delete()
        docs.removeAll { it.id == id }
    }

    fun get(id: String): TranslationDoc? = docs.firstOrNull { it.id == id }

    fun newId(): String = UUID.randomUUID().toString()

    fun folders(): List<String> = docs.map { it.folder }.distinct().sorted()

    fun sort() {
        val sorted = when (sortMode) {
            DocSort.UPDATED -> docs.sortedWith(compareByDescending<TranslationDoc> { it.pinned }.thenByDescending { it.updatedAt })
            DocSort.NAME -> docs.sortedWith(compareByDescending<TranslationDoc> { it.pinned }.thenBy { it.name })
            DocSort.PROGRESS -> docs.sortedWith(compareByDescending<TranslationDoc> { it.pinned }.thenByDescending { it.progress })
        }
        docs.clear()
        docs.addAll(sorted)
    }

    fun setSort(mode: DocSort) {
        sortMode = mode
        sort()
    }

    // ---------- 备份 / 恢复 ----------

    /** 导出全部文档为 JSON 文本。 */
    fun exportAllJson(): String = gson.toJson(docs.toList())

    /** 从备份 JSON 恢复文档；[replace] 为 true 时先清空现有文档。 */
    fun importAllJson(json: String, replace: Boolean): Result<Int> = runCatching {
        val type = com.google.gson.reflect.TypeToken.getParameterized(
            MutableList::class.java,
            TranslationDoc::class.java
        ).type
        val list: List<TranslationDoc> = gson.fromJson(json, type) ?: error("备份内容为空")
        if (replace) {
            synchronized(lock) {
                pending.clear()
                jobs.values.forEach { it.cancel() }
                jobs.clear()
            }
            dir?.listFiles()?.filter { it.extension == "json" }?.forEach { it.delete() }
            docs.clear()
        }
        var count = 0
        list.forEach { doc ->
            // 跳过已存在的文档，避免重复恢复产生副本
            if (doc.id.isNotBlank() && docs.none { it.id == doc.id }) {
                docs.add(doc)
                count++
            }
        }
        sort()
        docs.forEach { save(it, immediate = true) }
        count
    }
}
