package com.linetrans.app.data

import com.linetrans.app.model.ExportFormat
import com.linetrans.app.model.TranslationDoc
import com.linetrans.app.util.TextParser

object ExportManager {

    /** 按格式生成导出文本。 */
    fun buildText(doc: TranslationDoc, format: ExportFormat): String = when (format) {
        ExportFormat.TXT_TRANSLATED_ONLY ->
            doc.units.map { it.translation }.filter { it.isNotBlank() }.joinToString("\n")

        ExportFormat.TXT_BILINGUAL ->
            doc.units.joinToString("\n") { unit ->
                if (unit.translation.isBlank()) unit.source
                else unit.source + "\t" + unit.translation
            }

        ExportFormat.TXT_SOURCE_FALLBACK ->
            doc.units.joinToString("\n") { if (it.isTranslated) it.translation else it.source }

        ExportFormat.MARKDOWN_TABLE -> buildString {
            append("| # | 原文 | 译文 |\n")
            append("| --- | --- | --- |\n")
            doc.units.forEachIndexed { i, unit ->
                append("| ")
                append(i + 1)
                append(" | ")
                append(mdEscape(unit.source))
                append(" | ")
                append(mdEscape(unit.translation))
                append(" |\n")
            }
        }

        ExportFormat.CSV -> buildString {
            append("index,source,translation\n")
            doc.units.forEachIndexed { i, unit ->
                append(i + 1)
                append(',')
                append(TextParser.csvEscape(unit.source))
                append(',')
                append(TextParser.csvEscape(unit.translation))
                append('\n')
            }
        }

        ExportFormat.JSON -> buildString {
            append("{\n")
            append("  \"name\": \"").append(jsonEscape(doc.name)).append("\",\n")
            append("  \"mode\": \"").append(doc.unitMode.name).append("\",\n")
            append("  \"total\": ").append(doc.totalCount).append(",\n")
            append("  \"translated\": ").append(doc.translatedCount).append(",\n")
            append("  \"units\": [\n")
            doc.units.forEachIndexed { i, unit ->
                append("    { \"index\": ").append(i + 1)
                append(", \"source\": \"").append(jsonEscape(unit.source)).append("\"")
                append(", \"translation\": \"").append(jsonEscape(unit.translation)).append("\" }")
                if (i != doc.units.lastIndex) append(',')
                append('\n')
            }
            append("  ]\n}")
        }
    }

    /** 生成文件名（不含目录）。 */
    fun buildFileName(doc: TranslationDoc, format: ExportFormat): String {
        val base = doc.name.removeSuffix(".txt").ifBlank { "未命名文档" }
        val suffix = when (format) {
            ExportFormat.TXT_TRANSLATED_ONLY -> "译文"
            ExportFormat.TXT_BILINGUAL -> "对照"
            ExportFormat.TXT_SOURCE_FALLBACK -> "替换原文"
            ExportFormat.MARKDOWN_TABLE -> "对照表"
            ExportFormat.CSV -> "对照表"
            ExportFormat.JSON -> "数据"
        }
        return base + "_" + suffix + "." + format.extension
    }

    /** 写入已设置的数据文件夹，返回保存的文件名。 */
    fun export(context: android.content.Context, doc: TranslationDoc, format: ExportFormat): String {
        val settings = SettingsRepository.settings
        if (settings.storageDirUri.isBlank()) {
            throw IllegalStateException("请先在设置中选择用于存放数据的文件夹")
        }
        val fileName = buildFileName(doc, format)
        val text = buildText(doc, format)
        StorageManager.writeTextToFolder(context, settings.storageDirUri, fileName, text)
        return fileName
    }

    /** 分享用的纯文本（优先对照，未翻译的显示原文）。 */
    fun shareText(doc: TranslationDoc): String = buildText(doc, ExportFormat.TXT_BILINGUAL)

    private fun mdEscape(text: String): String =
        text.replace("|", "\\|").replace("\n", " ")

    private fun jsonEscape(text: String): String = buildString {
        text.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append(" ") else append(c)
            }
        }
    }
}
