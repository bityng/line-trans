package com.linetrans.app.data

import com.google.gson.JsonParser

/**
 * 备份格式：
 * { "version": 1, "exportedAt": 1690000000000, "docs": [ ... ], "settings": { ... } }
 */
object BackupManager {

    const val VERSION = 1

    fun buildJson(): String {
        val docs = DocRepository.exportAllJson()
        val settings = SettingsRepository.settingsJson()
        return buildString {
            append("{\"version\":")
            append(VERSION)
            append(",\"exportedAt\":")
            append(System.currentTimeMillis())
            append(",\"docs\":")
            append(docs)
            append(",\"settings\":")
            append(settings)
            append('}')
        }
    }

    private fun defaultFileName(): String =
        "linetrans-backup-" + SettingsRepository.today() + ".json"

    /** 导出备份到已设置的数据文件夹，返回文件名。 */
    fun exportToFolder(context: android.content.Context): String {
        val dir = SettingsRepository.settings.storageDirUri
        if (dir.isBlank()) throw IllegalStateException("请先在设置中选择数据文件夹")
        val name = defaultFileName()
        StorageManager.writeTextToFolder(context, dir, name, buildJson())
            ?: throw IllegalStateException("写入失败，请检查文件夹权限")
        return name
    }

    data class RestoreResult(val docs: Int, val settingsRestored: Boolean)

    /** 从备份文本恢复；兼容只有文档数组的旧备份。 */
    fun restore(json: String, replaceDocs: Boolean = false): Result<RestoreResult> = runCatching {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
        if (root != null && root.has("docs")) {
            val docsJson = root.get("docs").toString()
            val count = DocRepository.importAllJson(docsJson, replace = replaceDocs).getOrThrow()
            var settingsRestored = false
            if (root.has("settings") && !root.get("settings").isJsonNull) {
                SettingsRepository.restoreJson(root.get("settings").toString()).getOrThrow()
                settingsRestored = true
            }
            RestoreResult(count, settingsRestored)
        } else {
            RestoreResult(DocRepository.importAllJson(json, replace = replaceDocs).getOrThrow(), false)
        }
    }
}
