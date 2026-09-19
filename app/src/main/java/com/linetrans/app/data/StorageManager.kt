package com.linetrans.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object StorageManager {

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                return c.getString(idx) ?: "unnamed.txt"
            }
        }
        return "unnamed.txt"
    }

    fun readText(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开文件")
        val bytes = ByteArrayOutputStream().use { out ->
            input.copyTo(out)
            out.toByteArray()
        }
        return decodeText(bytes)
    }

    /** 优先按 UTF-8 解码，失败则回退 GB18030（兼容中文 Windows 的 ANSI/GBK 文本），并识别 UTF-16 BOM。 */
    fun decodeText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) return String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16LE"))
            if (b0 == 0xFE && b1 == 0xFF) return String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16BE"))
        }
        val offset = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) 3 else 0
        val utf8 = Charset.forName("UTF-8").newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8.decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("GB18030"))
        }
    }

    /** 写入已授权的文件夹，返回文档 Uri（失败返回 null）。 */
    fun writeTextToFolder(context: Context, treeUri: String, fileName: String, text: String): Uri? {
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return null
        val existing = dir.findFile(fileName)
        val doc = existing ?: dir.createFile(mimeOf(fileName), fileName) ?: return null
        return runCatching {
            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            }
            doc.uri
        }.getOrNull()
    }

    fun hasAccess(context: Context, treeUri: String): Boolean {
        if (treeUri.isBlank()) return false
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return false
        return dir.exists() && dir.canWrite()
    }

    private fun mimeOf(fileName: String): String = when {
        fileName.endsWith(".json") -> "application/json"
        fileName.endsWith(".md") -> "text/markdown"
        fileName.endsWith(".csv") -> "text/csv"
        else -> "text/plain"
    }
}
