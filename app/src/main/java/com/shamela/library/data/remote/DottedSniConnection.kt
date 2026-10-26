package com.shamela.library.data.remote

import org.bouncycastle.tls.DefaultTlsClient
import org.bouncycastle.tls.NameType
import org.bouncycastle.tls.ProtocolVersion
import org.bouncycastle.tls.ServerName
import org.bouncycastle.tls.ServerOnlyTlsAuthentication
import org.bouncycastle.tls.TlsAuthentication
import org.bouncycastle.tls.TlsClientProtocol
import org.bouncycastle.tls.TlsServerCertificate
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.util.Locale
import java.util.Vector
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * An HTTPS GET/HEAD to the book server whose TLS server name (SNI) is the host with a trailing dot,
 * "pub-….r2.dev." — the same host for Cloudflare, but not for filters that block every connection
 * whose SNI contains "r2.dev". Some networks do that with deep packet inspection that reassembles
 * split ClientHellos too, so [BookServerConnection]'s split handshake doesn't get through there.
 *
 * Android's TLS (Conscrypt) never sends a name ending in a dot, so the handshake is done by
 * BouncyCastle's TLS client. The certificate is still checked: chain against the system's trusted
 * CAs, name against the real host. HTTP/1.1 is spoken directly (one request per connection, which
 * is all the downloader needs), behind the [HttpURLConnection] API the callers already use.
 */
class DottedSniConnection(url: URL) : HttpURLConnection(url) {

    private var socket: Socket? = null
    private var tls: TlsClientProtocol? = null
    private var code = -1
    private var message: String? = null
    private val headers = LinkedHashMap<String, MutableList<String>>()
    private var body: InputStream? = null

    override fun connect() {
        if (connected) return
        require(url.protocol.equals("https", ignoreCase = true)) { "https only" }
        val host = url.host
        val port = if (url.port > 0) url.port else 443
        val requestHeaders = requestProperties
        val method = requestMethod

        try {
            val plain = openSocket(host, port, connectTimeout.takeIf { it > 0 } ?: 15_000)
            socket = plain // closed by close() if anything below fails
            plain.soTimeout = readTimeout.takeIf { it > 0 } ?: 30_000
            val protocol = TlsClientProtocol(plain.getInputStream(), plain.getOutputStream())
            tls = protocol
            protocol.connect(Client(host))

            val target = url.path.ifEmpty { "/" } + (url.query?.let { "?$it" } ?: "") // as given, already encoded
            val request = buildString {
                append(method).append(' ').append(target).append(" HTTP/1.1\r\n")
                append("Host: ").append(if (url.port > 0 && url.port != 443) "$host:${url.port}" else host).append("\r\n")
                requestHeaders.forEach { (name, values) ->
                    if (name == null || name.equals("Host", true) || name.equals("Connection", true)) return@forEach
                    values.forEach { append(name).append(": ").append(it).append("\r\n") }
                }
                if (requestHeaders.keys.none { it.equals("User-Agent", true) }) append("User-Agent: ShamelaLibrary\r\n")
                append("Connection: close\r\n\r\n")
            }
            protocol.outputStream.apply {
                write(request.toByteArray(Charsets.ISO_8859_1))
                flush()
            }

            val input = protocol.inputStream.buffered()
            val status = readLine(input) ?: throw IOException("empty response")
            val parts = status.split(' ', limit = 3)
            code = parts.getOrNull(1)?.toIntOrNull() ?: throw IOException("bad status line: $status")
            message = parts.getOrNull(2)
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                headers.getOrPut(line.substring(0, colon).trim().lowercase(Locale.ROOT)) { mutableListOf() } +=
                    line.substring(colon + 1).trim()
            }

            val length = header("content-length")?.toLongOrNull()
            body = when {
                method == "HEAD" || code == 204 || code == 304 -> ByteArrayInputStream(ByteArray(0))
                header("transfer-encoding")?.contains("chunked", true) == true -> ChunkedInput(input)
                length != null -> BoundedInput(input, length)
                else -> input
            }.let(::ClosingInput)
            connected = true
        } catch (e: Exception) {
            close()
            throw e as? IOException ?: IOException(e.message, e)
        }
    }

    private fun header(name: String) = headers[name]?.lastOrNull()

    /**
     * A TCP connection to one of the host's addresses, IPv4 first: many networks hand out IPv6
     * addresses (AAAA) without carrying IPv6 traffic, and the first address alone may be one.
     */
    private fun openSocket(host: String, port: Int, timeout: Int): Socket {
        val addresses = InetAddress.getAllByName(host).sortedBy { if (it is Inet4Address) 0 else 1 }
        var failure: IOException? = null
        for (address in addresses) {
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(address, port), timeout)
                return socket
            } catch (e: IOException) {
                socket.close()
                failure?.let(e::addSuppressed)
                failure = e
            }
        }
        throw failure ?: IOException("no address for $host")
    }

    override fun getResponseCode(): Int {
        connect()
        return code
    }

    override fun getResponseMessage(): String? {
        connect()
        return message
    }

    override fun getHeaderField(name: String?): String? {
        if (name == null) return null
        connect()
        return header(name.lowercase(Locale.ROOT))
    }

    override fun getHeaderFields(): Map<String, List<String>> {
        connect()
        return headers
    }

    override fun getContentLengthLong(): Long = getHeaderField("Content-Length")?.toLongOrNull() ?: -1L

    override fun getContentLength(): Int = contentLengthLong.let { if (it > Int.MAX_VALUE) -1 else it.toInt() }

    override fun getInputStream(): InputStream {
        connect()
        if (code >= 400) throw FileNotFoundException(url.toString())
        return body ?: throw IOException("no body")
    }

    override fun getErrorStream(): InputStream? = if (connected && code >= 400) body else null

    override fun disconnect() = close()

    override fun usingProxy() = false

    private fun close() {
        try {
            tls?.close()
        } catch (_: IOException) {
        }
        try {
            socket?.close()
        } catch (_: IOException) {
        }
    }

    /** Closing the body closes the connection (one request per connection). */
    private inner class ClosingInput(input: InputStream) : FilterInputStream(input) {
        override fun close() = this@DottedSniConnection.close()
    }

    private class Client(private val host: String) : DefaultTlsClient(BcTlsCrypto(SecureRandom())) {
        override fun getSupportedVersions(): Array<ProtocolVersion> =
            ProtocolVersion.TLSv13.downTo(ProtocolVersion.TLSv12)

        override fun getSNIServerNames(): Vector<ServerName> =
            Vector(listOf(ServerName(NameType.host_name, "$host.".toByteArray(Charsets.US_ASCII))))

        override fun getAuthentication(): TlsAuthentication = object : ServerOnlyTlsAuthentication() {
            override fun notifyServerCertificate(serverCertificate: TlsServerCertificate) {
                val factory = CertificateFactory.getInstance("X.509")
                val chain = (0 until serverCertificate.certificate.length).map { index ->
                    factory.generateCertificate(
                        ByteArrayInputStream(serverCertificate.certificate.getCertificateAt(index).encoded)
                    ) as X509Certificate
                }.toTypedArray()
                if (chain.isEmpty()) throw CertificateException("no server certificate")
                val authType = if (chain[0].publicKey is ECPublicKey) "ECDHE_ECDSA" else "ECDHE_RSA"
                checkTrusted(chain, authType, host)
                if (!matchesHost(chain[0], host)) throw CertificateException("certificate is not for $host")
            }
        }
    }

    companion object {
        private val systemTrust: X509TrustManager by lazy {
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(null as KeyStore?) }
                .trustManagers.filterIsInstance<X509TrustManager>().first()
        }

        /**
         * Checks [chain] against the system's trusted CAs. Android's trust manager wants the host too
         * when the app has a network security config (its hostname-aware method, also used by
         * X509TrustManagerExtensions); a plain JVM (unit tests) only has the two-argument one.
         */
        private fun checkTrusted(chain: Array<X509Certificate>, authType: String, host: String) {
            val hostAware = try {
                systemTrust.javaClass.getMethod(
                    "checkServerTrusted", Array<X509Certificate>::class.java, String::class.java, String::class.java,
                )
            } catch (e: NoSuchMethodException) {
                null
            }
            if (hostAware == null) {
                systemTrust.checkServerTrusted(chain, authType)
                return
            }
            try {
                hostAware.invoke(systemTrust, chain, authType, host)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.targetException
            }
        }

        /** The certificate's DNS names (subjectAltName) cover [host]; "*." matches one label. */
        internal fun matchesHost(certificate: X509Certificate, host: String): Boolean {
            val target = host.lowercase(Locale.ROOT).trimEnd('.')
            val names = certificate.subjectAlternativeNames.orEmpty()
                .filter { it.getOrNull(0) == 2 }
                .mapNotNull { (it.getOrNull(1) as? String)?.lowercase(Locale.ROOT)?.trimEnd('.') }
            return names.any { name ->
                if (name.startsWith("*.")) {
                    val suffix = name.substring(1)
                    target.endsWith(suffix) && target.indexOf('.') == target.length - suffix.length
                } else name == target
            }
        }

        private fun readLine(input: InputStream): String? {
            val line = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0) return if (line.isEmpty()) null else line.toString()
                if (b == '\n'.code) return line.toString().trimEnd('\r')
                line.append(b.toChar())
                if (line.length > 64 * 1024) throw IOException("header line too long")
            }
        }
    }

    private class BoundedInput(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
        override fun read(): Int {
            if (remaining <= 0) return -1
            return super.read().also { if (it >= 0) remaining-- }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = super.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            return n
        }

        override fun available(): Int = minOf(super.available().toLong(), remaining).toInt()
    }

    private class ChunkedInput(private val input: InputStream) : InputStream() {
        private var left = 0L
        private var done = false

        private fun nextChunk(): Boolean {
            if (done) return false
            if (left > 0) return true
            val size = readLine(input)?.substringBefore(';')?.trim()?.toLongOrNull(16)
                ?: throw IOException("bad chunk size")
            if (size == 0L) {
                while (readLine(input)?.isNotEmpty() == true) Unit // trailers
                done = true
                return false
            }
            left = size
            return true
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (!nextChunk()) return -1
            val n = input.read(b, off, minOf(len.toLong(), left).toInt())
            if (n < 0) throw IOException("connection closed inside a chunk")
            left -= n
            if (left == 0L) readLine(input) // CRLF after the chunk
            return n
        }
    }
}
