package com.pocketnas.pro.core

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Socket
import java.nio.charset.Charset

/**
 * 极简 HTTP/1.1 客户端：支持 Unix Domain Socket（旧定制内核）和 TCP（官方版 5244）。
 * 发送标准 HTTP 请求、解析标准 HTTP 响应。
 */
class LocalHttpClient private constructor(
    private val socketPath: String?,
    private val tcpHost: String?,
    private val tcpPort: Int,
    private val timeoutMs: Int = 10_000,
) {

    constructor(socketPath: String, timeoutMs: Int = 10_000) : this(socketPath, null, 0, timeoutMs)

    data class Response(
        val code: Int,
        val headers: Map<String, String>,
        val body: String,
    )

    fun get(path: String, token: String? = null): Response =
        request("GET", path, null, token)

    fun post(path: String, body: String? = null, token: String? = null): Response =
        request("POST", path, body, token)

    private fun connect(): java.net.Socket {
        return if (tcpHost != null) {
            java.net.Socket(tcpHost, tcpPort).apply { soTimeout = timeoutMs }
        } else {
            // Unix socket：用 LocalSocket 包装成 Socket 接口
            val ls = android.net.LocalSocket()
            ls.connect(android.net.LocalSocketAddress(socketPath, android.net.LocalSocketAddress.Namespace.FILESYSTEM))
            ls.soTimeout = timeoutMs
            // LocalSocket 不是 java.net.Socket 子类，我们用它的 stream
            UnixSocketWrapper(ls)
        }
    }

    /** 包装 LocalSocket 为 Closeable，提供 stream */
    private class UnixSocketWrapper(val ls: android.net.LocalSocket) : java.net.Socket() {
        override fun getInputStream() = ls.inputStream
        override fun getOutputStream() = ls.outputStream
        override fun close() { ls.close() }
    }

    fun request(
        method: String,
        path: String,
        body: String? = null,
        token: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        rawBody: ByteArray? = null,
    ): Response {
        val sock = connect()
        try {
            writeRequest(sock, method, path, body, token, extraHeaders, rawBody)
            return readResponse(sock.getInputStream())
        } finally {
            try { sock.close() } catch (_: Exception) {}
        }
    }

    fun requestStream(
        method: String,
        path: String,
        extraHeaders: Map<String, String> = emptyMap(),
        token: String? = null,
        writeBody: (java.io.OutputStream) -> Unit,
    ): Response {
        val sock = connect()
        try {
            val out = BufferedOutputStream(sock.getOutputStream(), 64 * 1024)
            val sb = StringBuilder()
            sb.append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
            sb.append("Host: localhost\r\n")
            if (token != null) sb.append("Authorization: ").append(token).append("\r\n")
            extraHeaders.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
            sb.append("Connection: close\r\n\r\n")
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
            writeBody(out)
            out.flush()
            return readResponse(sock.getInputStream())
        } finally {
            try { sock.close() } catch (_: Exception) {}
        }
    }

    fun openStream(
        method: String,
        path: String,
        extraHeaders: Map<String, String> = emptyMap(),
        token: String? = null,
    ): StreamResponse {
        val sock = connect()
        val out = BufferedOutputStream(sock.getOutputStream(), 64 * 1024)
        val sb = StringBuilder()
        sb.append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
        sb.append("Host: localhost\r\n")
        if (token != null) sb.append("Authorization: ").append(token).append("\r\n")
        extraHeaders.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.flush()

        val input = sock.getInputStream()
        val statusLine = readLine(input) ?: throw IllegalStateException("no response")
        val parts = statusLine.split(" ")
        val code = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        val body: InputStream = if (headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true)
            ChunkedInputStream(input) else input
        return StreamResponse(code, headers, body, sock)
    }

    data class StreamResponse(
        val code: Int,
        val headers: Map<String, String>,
        val body: InputStream,
        private val sock: java.net.Socket,
    ) : java.io.Closeable {
        override fun close() {
            try { body.close() } catch (_: Exception) {}
            try { sock.close() } catch (_: Exception) {}
        }
    }

    private inner class ChunkedInputStream(private val raw: InputStream) : InputStream() {
        private var remaining = 0L
        private var finished = false
        override fun read(): Int {
            val buf = ByteArray(1)
            val n = read(buf, 0, 1)
            return if (n == -1) -1 else buf[0].toInt() and 0xFF
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (finished) return -1
            while (remaining <= 0) {
                val line = readLine(raw) ?: run { finished = true; return -1 }
                val size = line.trim().split(';')[0].toIntOrNull(16) ?: run { finished = true; return -1 }
                if (size == 0) {
                    while (true) { val l = readLine(raw) ?: break; if (l.isEmpty()) break }
                    finished = true; return -1
                }
                remaining = size.toLong()
            }
            val n = raw.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n == -1) { finished = true; return -1 }
            remaining -= n
            if (remaining == 0L) readLine(raw)
            return n
        }
    }

    private fun writeRequest(
        sock: java.net.Socket,
        method: String,
        path: String,
        body: String?,
        token: String?,
        extraHeaders: Map<String, String>,
        rawBody: ByteArray?,
    ) {
        val bodyBytes = rawBody ?: (body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0))
        val out = BufferedOutputStream(sock.getOutputStream(), 8192)
        val sb = StringBuilder()
        sb.append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
        sb.append("Host: localhost\r\n")
        if (token != null) sb.append("Authorization: ").append(token).append("\r\n")
        if (rawBody == null) sb.append("Content-Type: application/json; charset=utf-8\r\n")
        extraHeaders.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("Content-Length: ").append(bodyBytes.size).append("\r\n")
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        if (bodyBytes.isNotEmpty()) out.write(bodyBytes)
        out.flush()
    }

    private fun readResponse(input: InputStream): Response {
        val statusLine = readLine(input) ?: throw IllegalStateException("no response")
        val parts = statusLine.split(" ")
        val code = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        val body = when {
            headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true -> readChunked(input)
            headers.containsKey("content-length") -> readExact(input, headers["content-length"]!!.toIntOrNull() ?: 0)
            else -> readToEof(input)
        }
        return Response(code, headers, body.toString(Charsets.UTF_8))
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream(128)
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString(Charsets.UTF_8)
            if (b == '\n'.code) return buf.toString(Charsets.UTF_8).trimEnd('\r')
            buf.write(b)
        }
    }

    private fun readExact(input: InputStream, len: Int): ByteArray {
        val out = ByteArrayOutputStream(len)
        var remaining = len
        val tmp = ByteArray(8192)
        while (remaining > 0) {
            val n = input.read(tmp, 0, minOf(tmp.size, remaining))
            if (n == -1) break
            out.write(tmp, 0, n)
            remaining -= n
        }
        return out.toByteArray()
    }

    private fun readChunked(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(input) ?: break
            val size = sizeLine.trim().split(';')[0].toIntOrNull(16) ?: break
            if (size == 0) {
                while (true) { val l = readLine(input) ?: break; if (l.isEmpty()) break }
                break
            }
            out.write(readExact(input, size))
            readLine(input)
        }
        return out.toByteArray()
    }

    private fun readToEof(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val tmp = ByteArray(8192)
        while (true) {
            val n = input.read(tmp)
            if (n == -1) break
            out.write(tmp, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        /** TCP 模式：连 127.0.0.1:port（官方版内核） */
        fun tcp(host: String = "127.0.0.1", port: Int = 5244, timeoutMs: Int = 10_000): LocalHttpClient =
            LocalHttpClient(null, host, port, timeoutMs)
    }
}
