package com.linetrans.app.server

import android.content.Context
import com.linetrans.app.data.SettingsRepository
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * 局域网 Web 终端服务。提供 xterm.js 页面（/terminal），通过 WebSocket（/ws）桥接手机本地 shell。
 *
 * 注意：WebSocket 的升级握手由 [NanoWSD.serve] 处理，因此这里只能覆盖 [serveHttp]，
 * 覆盖 [serve] 会让 /ws 永远走不到升级分支。
 */
class WebTerminalServer(
    private val context: Context,
    port: Int
) : NanoWSD(port) {

    private val api = WebApi(context)

    @Volatile
    private var started = false

    @Synchronized
    fun startServer() {
        if (started) return
        start(SOCKET_READ_TIMEOUT, false)
        started = true
    }

    override fun serveHttp(session: IHTTPSession): Response {
        if (!tokenAllowed(session)) return unauthorized()
        val uri = session.uri.removePrefix("/")
        return when {
            uri.startsWith("api/") -> api.handle(session)
            uri.isEmpty() || uri == "index.html" || uri == "app" -> serveWebApp("index.html", "text/html; charset=utf-8")
            uri == "app.js" -> serveWebApp("app.js", "text/javascript; charset=utf-8")
            uri == "style.css" -> serveWebApp("style.css", "text/css; charset=utf-8")
            uri == "terminal" -> serveHtml()
            uri == "health" -> newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", "ok")
            uri.startsWith("web_terminal/") -> serveAsset(uri.removePrefix("web_terminal/"))
            uri == "favicon.ico" -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "")
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "404 not found")
        }
    }

    /** 网页翻译台的静态资源（assets/web/）。 */
    private fun serveWebApp(name: String, mime: String): Response {
        val bytes = runCatching { context.assets.open("web/" + name).readBytes() }.getOrNull()
            ?: return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain; charset=utf-8",
                "缺少 assets/web/$name"
            )
        return newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong())
    }

    /** 设置了访问令牌时，HTTP 与 WebSocket 都必须携带 ?token=xxx。 */
    private fun tokenAllowed(session: IHTTPSession): Boolean {
        val expected = SettingsRepository.settings.webServerToken.trim()
        if (expected.isEmpty()) return true
        val provided = session.parameters?.get("token")?.firstOrNull()
        return provided == expected
    }

    private fun unauthorized(): Response = newFixedLengthResponse(
        Response.Status.UNAUTHORIZED,
        "text/plain; charset=utf-8",
        "401 未授权：请在地址后加上 ?token=<访问令牌>"
    )

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

    override fun openWebSocket(handshake: IHTTPSession): NanoWSD.WebSocket =
        if (tokenAllowed(handshake)) TerminalWebSocket(handshake) else RejectedWebSocket(handshake)

    override fun stop() {
        super.stop()
    }

    /** 令牌错误时直接关闭连接。 */
    private inner class RejectedWebSocket(handshake: IHTTPSession) : NanoWSD.WebSocket(handshake) {
        override fun onOpen() {
            runCatching {
                close(NanoWSD.WebSocketFrame.CloseCode.PolicyViolation, "unauthorized", true)
            }
        }

        override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {}
        override fun onMessage(message: NanoWSD.WebSocketFrame) {}
        override fun onPong(pong: NanoWSD.WebSocketFrame) {}
        override fun onException(e: IOException) {}
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
                        // 流已结束
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
