package com.linetrans.app.data

import com.linetrans.app.model.TranslationDoc

object ExportManager {

    enum class Mode(val label: String) {
        TRANSLATED_ONLY("仅导出已翻译文本"),
        TRANSLATED_REPLACES_SOURCE("已翻译替换源文本，未翻译显示为源文本")
    }

    fun buildExportText(doc: TranslationDoc, mode: Mode): String {
        return when (mode) {
            Mode.TRANSLATED_ONLY -> doc.units
                .map { it.translation }
                .filter { it.isNotBlank() }
                .joinToString("\n")

            Mode.TRANSLATED_REPLACES_SOURCE -> doc.units
                .joinToString("\n") { if (it.isTranslated) it.translation else it.source }
        }
    }

    fun export(context: android.content.Context, doc: TranslationDoc, mode: Mode) {
        val settings = SettingsRepository.settings
        if (settings.storageDirUri.isBlank()) {
            throw IllegalStateException("请先在设置中选择用于存放数据的文件夹")
        }
        val text = buildExportText(doc, mode)
        val safeName = doc.name.removeSuffix(".txt")
        val fileName = safeName + "_" + when (mode) {
            Mode.TRANSLATED_ONLY -> "已翻译.txt"
            Mode.TRANSLATED_REPLACES_SOURCE -> "对照.txt"
        }
        StorageManager.writeTextToFolder(context, settings.storageDirUri, fileName, text)
    }
}
