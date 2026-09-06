package com.linetrans.app.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * 局域网 Web 终端服务。提供 xterm.js 页面（/terminal），通过 WebSocket（/ws）桥接手机本地 shell。
 */
class WebTerminalServer(
    private val context: Context,
    port: Int
) : NanoWSD(port) {

    @Volatile
    private var started = false

    @Synchronized
    fun startServer() {
        if (started) return
        start(SOCKET_READ_TIMEOUT, false)
        started = true
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.removePrefix("/")
        return when {
            uri == "" || uri == "terminal" -> serveHtml()
            uri == "health" -> newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", "ok")
            uri.startsWith("web_terminal/") -> serveAsset(uri.removePrefix("web_terminal/"))
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "404 not found")
        }
    }

    private fun serveHtml(): Response {
        val html = runCatching {
            context.assets.open("web_terminal.html").bufferedReader().readText()
        }.getOrElse { "<h1>web_terminal.html missing</h1>" }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun serveAsset(name: String): Response {
        val mime = when {
            name.endsWith(".js") -> "text/javascript; charset=utf-8"
            name.endsWith(".css") -> "text/css; charset=utf-8"
            else -> "application/octet-stream"
        }
        val bytes = runCatching {
            context.assets.open("web_terminal/" + name).readBytes()
        }.getOrNull()
        return if (bytes != null) {
            newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong())
        } else {
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "404")
        }
    }

    override fun openWebSocket(handshake: IHTTPSession): NanoWSD.WebSocket = TerminalWebSocket(handshake)

    override fun stop() {
        super.stop()
    }

    inner class TerminalWebSocket(handshake: IHTTPSession) : NanoWSD.WebSocket(handshake) {
        private var process: Process? = null
        private var readerThread: Thread? = null

        override fun onOpen() {
            try {
                val pb = ProcessBuilder("/system/bin/sh")
                pb.redirectErrorStream(true)
                process = pb.start()
                readerThread = Thread {
                    try {
                        process?.inputStream?.bufferedReader()?.forEachLine { line ->
                            sendSafe(line + "\r\n")
                        }
                    } catch (_: Exception) {
                        // stream ended
                    }
                }.apply {
                    isDaemon = true
                    start()
                }
            } catch (e: Exception) {
                sendSafe("shell 启动失败: " + e.message)
            }
        }

        override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            readerThread?.interrupt()
            process?.destroy()
            process = null
        }

        override fun onException(e: IOException) {
            readerThread?.interrupt()
            process?.destroy()
            process = null
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            try {
                val text = message.textPayload
                val os = process?.outputStream ?: return
                os.write(text.toByteArray())
                os.write("\n".toByteArray())
                os.flush()
            } catch (_: Exception) {
            }
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame) {
        }

        private fun sendSafe(payload: String) {
            try {
                synchronized(this) {
                    send(payload)
                }
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val SOCKET_READ_TIMEOUT = 5000
    }
}
