package com.pocketnas.pro.core

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * 本地流代理：把 127.0.0.1 TCP 端口上的 HTTP 请求转发到 OpenList 内核的
 * Unix Domain Socket（即「/d/...」媒体下载流）。Android 自带的 MediaPlayer /
 * VideoView 只认 http(s)/file，不认识 unix socket；通过本代理，播放器直接
 * setDataSource("http://127.0.0.1:<port>/d/...") 即可流式播放（含 Range 断点）。
 *
 * 实现方式：原样转发请求行与全部请求头；内核响应（含 chunked / Content-Length）
 * 以字节流方式边读边转写，不缓存整个媒体到内存。
 */
class KernelHttpProxy(private val kernelSocketPath: String) {

    companion object {
        private const val TAG = "KernelHttpProxy"
    }

    @Volatile
    private var server: ServerSocket? = null
    @Volatile
    private var running = false

    /** 启动代理，返回本地端口；已在运行则返回既有端口 */
    @Synchronized
    fun start(): Int {
        server?.let { return it.localPort }
        val srv = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        srv.reuseAddress = true
        server = srv
        running = true
        val port = srv.localPort
        Thread({ acceptLoop(srv) }, "kernel-proxy").apply {
            isDaemon = true
            start()
        }
        Log.i(TAG, "本地媒体代理已启动: 127.0.0.1:$port")
        return port
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        server = null
    }

    private fun acceptLoop(srv: ServerSocket) {
        while (running) {
            try {
                val client = srv.accept()
                Thread({ handle(client) }, "proxy-conn").apply {
                    isDaemon = true
                    start()
                }
            } catch (_: Exception) {
                if (running) Thread.sleep(50)
            }
        }
    }

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 60000
            val input = client.getInputStream()
            val output = BufferedOutputStream(client.getOutputStream(), 256 * 1024)

            // 1. 读请求行 + 请求头（原样转发）
            val reqLine = readLine(input) ?: return
            LogStore.log("PROXY", "req $reqLine")
            val sb = StringBuilder()
            sb.append(reqLine).append("\r\n")
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Host:", ignoreCase = true)) continue // 用内核自己的 Host
                sb.append(line).append("\r\n")
            }
            sb.append("Host: localhost\r\n\r\n")

            // 2. 连接内核 TCP 5244 并转发
            val kernel = java.net.Socket("127.0.0.1", 5244)
            kernel.soTimeout = 60000
            try {
                val kOut = BufferedOutputStream(kernel.getOutputStream(), 256 * 1024)
                kOut.write(sb.toString().toByteArray(Charsets.UTF_8))
                kOut.flush()

                // 3. 原样转发响应：状态行 + 头 + 体（字节流）
                val kIn = kernel.getInputStream()
                val statusLine = readLine(kIn) ?: return
                val headSb = StringBuilder()
                headSb.append(statusLine).append("\r\n")
                while (true) {
                    val line = readLine(kIn) ?: break
                    if (line.isEmpty()) break
                    headSb.append(line).append("\r\n")
                }
                headSb.append("\r\n")
                output.write(headSb.toString().toByteArray(Charsets.UTF_8))
                output.flush()

                copyStream(kIn, output)
                output.flush()
            } finally {
                try { kernel.close() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            // 连接中断（播放器 seek / 主动断开）属正常
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream(256)
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString(Charsets.UTF_8).trimEnd('\r')
            if (b == '\n'.code) return buf.toString(Charsets.UTF_8).trimEnd('\r')
            buf.write(b)
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buf = ByteArray(256 * 1024)
        while (true) {
            val n = try { input.read(buf) } catch (_: Exception) { -1 }
            if (n == -1) break
            try {
                output.write(buf, 0, n)
            } catch (_: Exception) {
                break
            }
        }
    }
}
