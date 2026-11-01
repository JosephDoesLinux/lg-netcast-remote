package dev.netcastremote.protocol

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class Incoming(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: ByteArray,
)

data class Outgoing(
    val status: Int = 200,
    val reason: String = "OK",
    val contentType: String = "text/xml; charset=utf-8",
    val body: ByteArray = ByteArray(0),
)

class MiniHttpServer(
    private val handler: (Incoming) -> Outgoing,
) : AutoCloseable {
    private val socket = java.net.ServerSocket(0)
    val port: Int = socket.localPort
    private val running = AtomicBoolean(true)
    private val pool = Executors.newCachedThreadPool()
    private val thread = Thread({
        while (running.get()) {
            val client = try {
                socket.accept()
            } catch (_: Exception) {
                break
            }
            pool.execute {
                try {
                    client.soTimeout = 8_000
                    val request = readRequest(client.getInputStream())
                    val response = handler(request)
                    writeResponse(client, response)
                } catch (_: Exception) {
                } finally {
                    try {
                        client.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }, "netcast-http").also { it.isDaemon = true }

    fun start() {
        thread.start()
    }

    override fun close() {
        running.set(false)
        try {
            socket.close()
        } catch (_: Exception) {
        }
        pool.shutdownNow()
    }
}

class RawHttpClient : AutoCloseable {
    private val lock = Any()
    private var live: Socket? = null
    private var liveKey: String? = null

    fun get(host: String, port: Int, path: String): HttpResult =
        execute(host, port, "GET", path, null, keepAlive = false)

    fun post(
        host: String,
        port: Int,
        path: String,
        body: ByteArray,
        contentType: String,
        keepAlive: Boolean,
        extraHeaders: Map<String, String> = emptyMap(),
    ): HttpResult = execute(host, port, "POST", path, body, keepAlive, contentType, extraHeaders)

    fun execute(
        host: String,
        port: Int,
        method: String,
        path: String,
        body: ByteArray?,
        keepAlive: Boolean,
        contentType: String = "text/xml; charset=utf-8",
        extraHeaders: Map<String, String> = emptyMap(),
    ): HttpResult = synchronized(lock) {
        val key = "$host:$port"
        val socket = if (keepAlive && liveKey == key && live?.isClosed == false) {
            live!!
        } else {
            closeLive()
            Socket().also {
                it.connect(InetSocketAddress(host, port), 4_000)
                it.soTimeout = 8_000
                it.tcpNoDelay = true
            }
        }
        try {
            val payload = body ?: ByteArray(0)
            val header = buildString {
                append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                append("Host: ").append(host).append(':').append(port).append("\r\n")
                append("Cache-Control: no-cache\r\n")
                append("User-Agent: ").append(UdapXml.USER_AGENT).append("\r\n")
                append("Connection: ").append(if (keepAlive) "Keep-Alive" else "Close").append("\r\n")
                extraHeaders.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
                if (method != "GET") {
                    append("Content-Type: ").append(contentType).append("\r\n")
                    append("Content-Length: ").append(payload.size).append("\r\n")
                }
                append("\r\n")
            }
            val out = socket.getOutputStream()
            out.write(header.toByteArray(Charsets.US_ASCII))
            if (payload.isNotEmpty()) out.write(payload)
            out.flush()
            val result = readResult(socket.getInputStream())
            val close = !keepAlive || result.headers["connection"].equals("close", ignoreCase = true)
            if (close) {
                socket.close()
                if (live === socket) closeLive()
            } else {
                live = socket
                liveKey = key
            }
            result
        } catch (ex: Exception) {
            try {
                socket.close()
            } catch (_: Exception) {
            }
            closeLive()
            throw ex
        }
    }

    override fun close() = synchronized(lock) { closeLive() }

    private fun closeLive() {
        try {
            live?.close()
        } catch (_: Exception) {
        }
        live = null
        liveKey = null
    }
}

internal fun readRequest(input: java.io.InputStream): Incoming {
    val header = readUntilBlank(input)
    val text = header.toString(Charsets.ISO_8859_1)
    val lines = text.split("\r\n")
    val request = lines.firstOrNull().orEmpty().split(' ')
    val headers = headerMap(lines.drop(1))
    val length = headers["content-length"]?.toIntOrNull() ?: 0
    val body = if (length > 0) readExact(input, length) else ByteArray(0)
    return Incoming(
        method = request.getOrElse(0) { "" },
        path = request.getOrElse(1) { "/" },
        headers = headers,
        body = body,
    )
}

internal fun readResult(input: java.io.InputStream): HttpResult {
    val header = readUntilBlank(input)
    val text = header.toString(Charsets.ISO_8859_1)
    val lines = text.split("\r\n")
    val status = lines.firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull() ?: 0
    val headers = headerMap(lines.drop(1))
    val length = headers["content-length"]?.toIntOrNull() ?: 0
    val body = if (length > 0) readExact(input, length) else ByteArray(0)
    return HttpResult(status, headers, body)
}

private fun writeResponse(socket: Socket, response: Outgoing) {
    val header = buildString {
        append("HTTP/1.1 ").append(response.status).append(' ').append(response.reason).append("\r\n")
        append("Content-Type: ").append(response.contentType).append("\r\n")
        append("Content-Length: ").append(response.body.size).append("\r\n")
        append("Connection: Close\r\n")
        append("Cache-Control: no-store\r\n\r\n")
    }
    val out = socket.getOutputStream()
    out.write(header.toByteArray(Charsets.US_ASCII))
    if (response.body.isNotEmpty()) out.write(response.body)
    out.flush()
}

private fun headerMap(lines: List<String>): Map<String, String> {
    val headers = linkedMapOf<String, String>()
    lines.forEach { line ->
        val idx = line.indexOf(':')
        if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
    }
    return headers
}

private fun readUntilBlank(input: java.io.InputStream): ByteArray {
    val out = ByteArrayOutputStream()
    var matched = 0
    val needle = byteArrayOf(13, 10, 13, 10)
    while (matched < 4) {
        val b = input.read()
        if (b < 0) break
        out.write(b)
        matched = if (b.toByte() == needle[matched]) matched + 1 else if (b.toByte() == needle[0]) 1 else 0
    }
    return out.toByteArray()
}

private fun readExact(input: java.io.InputStream, length: Int): ByteArray {
    val buf = ByteArray(length)
    var off = 0
    while (off < length) {
        val n = input.read(buf, off, length - off)
        if (n < 0) break
        off += n
    }
    return if (off == length) buf else buf.copyOf(off)
}
