package com.linetrans.app.util

import com.linetrans.app.model.TranslationUnit
import com.linetrans.app.model.UnitMode

object TextParser {

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
        val regex = Regex("[^。！？!?；;.…\\n]+[。！？!?；;.…\\n]*[”’\"'）)]*")
        return regex.findAll(text)
            .map { it.value.trim() }
            .filter { it.isNotEmpty() }
            .map { TranslationUnit(it) }
            .toList()
    }

    fun detectLanguage(text: String): String {
        if (text.isBlank()) return "auto"
        val cjk = text.count { it in '\u4e00'..'\u9fff' }
        val latin = text.count { it.isLetter() && it.code < 128 }
        return when {
            cjk > latin -> "zh-CN"
            latin > cjk -> "en"
            else -> "auto"
        }
    }

    /** Detect source/translation already present in a line (tab or => separated). */
    fun splitSourceTranslation(line: String): Pair<String, String>? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains("\t")) {
            val parts = trimmed.split("\t", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return parts[0].trim() to parts[1].trim()
            }
        }
        val arrow = Regex("(.+?)\\s*=>\\s*(.+)").find(trimmed)
        if (arrow != null) {
            val s = arrow.groupValues[1].trim()
            val t = arrow.groupValues[2].trim()
            if (s.isNotBlank() && t.isNotBlank()) return s to t
        }
        return null
    }

    fun buildLineUnit(source: String, translation: String = ""): TranslationUnit = TranslationUnit(source, translation)
}
