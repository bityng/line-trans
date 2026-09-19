package com.linetrans.app.util

import com.linetrans.app.model.TranslationUnit
import com.linetrans.app.model.UnitMode

object TextParser {

    /** SRT / VTT 时间轴行，例如 `00:00:01,000 --> 00:00:02,500`。 */
    private val timecodeRegex = Regex("^\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3}\\s*-->\\s*.*$")

    /** 纯数字序号行（字幕序号、有序列表）。 */
    private val indexOnlyRegex = Regex("^\\d{1,4}[.、)]?$")

    /** Markdown 行首标记。 */
    private val mdPrefixRegex = Regex("^(#{1,6}\\s+|>\\s+|[-*+]\\s+|\\d+\\.\\s+)")

    /**
     * 智能清理：去掉字幕时间轴、序号行与 Markdown 行首标记，并压缩多余空行。
     * 仅在用户勾选“智能清理”时使用。
     */
    fun smartClean(text: String): String {
        val kept = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { !timecodeRegex.matches(it) }
            .filter { !indexOnlyRegex.matches(it) }
            .filter { it != "WEBVTT" && !it.startsWith("WEBVTT") }
            .map { mdPrefixRegex.replace(it, "") }
            .filter { it.isNotEmpty() }
        return kept.joinToString("\n")
    }

    /**
     * 判断文本是否像 CSV / 制表符对照表：大部分行都能切成两段。
     */
    fun looksLikeTable(text: String): Boolean {
        val lines = text.lines().filter { it.isNotBlank() }.take(20)
        if (lines.size < 2) return false
        val matched = lines.count { splitSourceTranslation(it) != null }
        return matched >= lines.size * 0.6
    }

    fun parse(text: String, mode: UnitMode): List<TranslationUnit> {
        return when (mode) {
            UnitMode.LINE -> parseLines(text)
            UnitMode.SENTENCE -> parseSentences(text)
        }
    }

    private fun parseLines(text: String): List<TranslationUnit> {
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { TranslationUnit(it) }
    }

    private fun parseSentences(text: String): List<TranslationUnit> {
        // 以中英文句号/问号/感叹号/分号/省略号为界切分句子，并保留标点；
        // 同时把换行作为句子边界，英文缩写等场景下也不会整段吞掉。
        val regex = Regex("[^。！？!?；;.…\\n]+[。！？?!；;.…\\n]*[”’\"'）)]*")
        return regex.findAll(text)
            .map { it.value.trim() }
            .filter { it.isNotEmpty() }
            .map { TranslationUnit(it) }
            .toList()
    }

    fun detectLanguage(text: String): String {
        if (text.isBlank()) return "auto"
        val cjk = text.count { it in '\u4e00'..'\u9fff' }
        val kana = text.count { it in '\u3040'..'\u30ff' }
        val hangul = text.count { it in '\uac00'..'\ud7af' }
        val latin = text.count { it.isLetter() && it.code < 128 }
        return when {
            kana > latin -> "ja"
            hangul > latin -> "ko"
            cjk > latin -> "zh-CN"
            latin > cjk -> "en"
            else -> "auto"
        }
    }

    /** 解析一行里已经存在的「原文 ⇥ 译文」（Tab、`,`、`=>`、`|` 分隔）。 */
    fun splitSourceTranslation(line: String): Pair<String, String>? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains("\t")) {
            val parts = trimmed.split("\t", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return parts[0].trim() to parts[1].trim()
            }
        }
        for (sep in listOf("=>", "⇥", "|")) {
            val idx = trimmed.indexOf(sep)
            if (idx > 0) {
                val s = trimmed.substring(0, idx).trim()
                val t = trimmed.substring(idx + sep.length).trim()
                if (s.isNotBlank() && t.isNotBlank()) return s to t
            }
        }
        // CSV：仅当恰好两列且第二列不像纯英文原文时采用
        if (trimmed.contains(",") && !trimmed.contains("，")) {
            val parts = splitCsvLine(trimmed)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return parts[0].trim().trim('"') to parts[1].trim().trim('"')
            }
        }
        return null
    }

    /** 简易 CSV 行拆分，支持双引号包裹与转义引号。 */
    fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    out.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    fun csvEscape(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuote) "\"" + escaped + "\"" else escaped
    }

    fun buildLineUnit(source: String, translation: String = ""): TranslationUnit =
        TranslationUnit(source, translation)
}
