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

    /** 优先按 UTF-8 解码，若失败则回退 GB18030（兼容中文 Windows 的 ANSI/GBK 文本）。 */
    fun decodeText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
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

    fun writeTextToFolder(context: Context, treeUri: String, fileName: String, text: String) {
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return
        val existing = dir.findFile(fileName)
        val doc = existing ?: dir.createFile("text/plain", fileName)
        if (doc == null) return
        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    fun hasAccess(context: Context, treeUri: String): Boolean {
        if (treeUri.isBlank()) return false
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return false
        return dir.exists() && dir.canWrite()
    }
}
