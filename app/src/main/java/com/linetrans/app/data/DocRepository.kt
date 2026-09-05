package com.linetrans.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.linetrans.app.model.TranslationDoc
import java.io.File
import java.util.UUID

object DocRepository {
    private val gson = Gson()
    private var dir: File? = null

    val docs = mutableStateListOf<TranslationDoc>()

    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.filesDir, "docs").apply { mkdirs() }
        loadAll()
    }

    private fun loadAll() {
        docs.clear()
        dir?.listFiles()?.filter { it.extension == "json" }?.forEach { f ->
            val doc = runCatching { gson.fromJson(f.readText(), TranslationDoc::class.java) }.getOrNull()
            if (doc != null) docs.add(doc)
        }
        sort()
    }

    fun save(doc: TranslationDoc) {
        doc.updatedAt = System.currentTimeMillis()
        val f = File(dir, "${doc.id}.json")
        f.writeText(gson.toJson(doc))
        val idx = docs.indexOfFirst { it.id == doc.id }
        if (idx >= 0) docs[idx] = doc else docs.add(doc)
        sort()
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        docs.removeAll { it.id == id }
    }

    fun get(id: String): TranslationDoc? = docs.firstOrNull { it.id == id }

    fun newId(): String = UUID.randomUUID().toString()

    private fun sort() {
        val sorted = docs.sortedByDescending { it.updatedAt }
        docs.clear()
        docs.addAll(sorted)
    }
}
