package com.shamela.library.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore
import org.conscrypt.Conscrypt

/**
 * Connections to the book server (a Cloudflare R2 `*.r2.dev` host).
 *
 * Some networks block any TLS connection whose first message names `r2.dev` (deep packet inspection
 * of the SNI field): the download fails at once (connection reset, or an injected non-TLS reply) on
 * that network while it works on others or through a VPN. Two ways around it are tried in turn when
 * the plain connection fails like that:
 *
 *  1. [DottedSniConnection]: the server name is sent as "host." (trailing dot). Cloudflare treats it
 *     as the same host; the filters seen so far don't match it, even those that reassemble split
 *     handshakes.
 *  2. The split handshake: the first message is split in two TLS records, sent as separate TCP
 *     segments, so a filter that looks at single packets doesn't see the host name in one piece.
 *
 * The way that worked is tried first from then on, also after a restart (it is saved), so a normal
 * network never pays for it and a filtered one doesn't probe on each launch.
 */
object BookServerConnection {
    private const val TAG = "BookServerConnection"

    private enum class Route { PLAIN, DOTTED_SNI, SPLIT }

    /** The route that worked last, kept across launches; null until a connection succeeded. */
    @Volatile
    private var lastWorking: Route? = null

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Loads the remembered route (reads disk: call off the main thread). */
    fun init(context: Context) {
        if (prefs != null) return
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (lastWorking == null) {
            lastWorking = stored.getString(KEY_ROUTE, null)?.let { name -> Route.entries.firstOrNull { it.name == name } }
        }
        prefs = stored
    }

    /** True once a working route is known (so the first download needn't find one). */
    val isRouteKnown: Boolean get() = lastWorking != null

    private const val PREFS = "book_server"
    private const val KEY_ROUTE = "route"

    /**
     * Opens [url] and returns the connection once it is connected (response code read), going
     * around SNI filters when the plain connection is blocked. [configure] sets method, headers
     * and timeouts.
     */
    fun open(url: String, configure: HttpURLConnection.() -> Unit): HttpURLConnection {
        if (!url.startsWith("https://")) return connect(url, Route.PLAIN, configure)
        val remembered = lastWorking
        val order = listOfNotNull(remembered) + Route.entries.filter { it != remembered }
        var failure: IOException? = null
        for (route in order) {
            try {
                return connect(url, route, configure).also {
                    if (route != remembered) {
                        Log.i(TAG, "connected via $route")
                        prefs?.edit()?.putString(KEY_ROUTE, route.name)?.apply()
                    }
                    lastWorking = route
                }
            } catch (e: IOException) {
                // Only a blocked-looking failure is worth another route; a dead network isn't.
                if (!looksBlocked(e)) throw e
                Log.w(TAG, "$route failed (${e.message})")
                failure = failure ?: e
            }
        }
        throw failure ?: IOException("no route to the book server")
    }

    private fun connect(url: String, route: Route, configure: HttpURLConnection.() -> Unit): HttpURLConnection {
        val connection = when (route) {
            Route.DOTTED_SNI -> DottedSniConnection(URL(url))
            else -> (URL(url).openConnection() as HttpURLConnection).also {
                if (route == Route.SPLIT && it is HttpsURLConnection) it.sslSocketFactory = SplitHelloSocketFactory
            }
        }
        connection.configure()
        try {
            connection.responseCode
        } catch (e: IOException) {
            connection.disconnect()
            throw e
        }
        return connection
    }

    /**
     * Resets, handshake failures, garbled replies and refused connections right at the start (what
     * SNI filters do); not timeouts or a host that doesn't resolve (a dead network).
     */
    private fun looksBlocked(e: IOException): Boolean {
        // The HTTP stack tries every address of the host and throws the last failure, with the
        // earlier ones as suppressed exceptions: a blocked IPv4 handshake followed by an IPv6
        // address that can't be reached shows up as "Failed to connect to …[2606:…]". So look at
        // the whole tree, not just the exception thrown.
        val all = mutableListOf<Throwable>()
        fun collect(t: Throwable?) {
            if (t == null || all.any { it === t } || all.size > 32) return
            all += t
            collect(t.cause)
            t.suppressed.forEach(::collect)
        }
        collect(e)
        val text = all.joinToString(" ") { "${it.javaClass.simpleName} ${it.message}" }
        Log.d(TAG, "failure: $text")
        return listOf(
            "reset", "SSLHandshake", "SSLException", "SSLProtocol", "Connection closed", "EOF", "ECONNRESET",
            "broken pipe", "TlsFatalAlert", "TlsNoCloseNotify", "protocol version", "record", "packet length",
            // The stack may report only its last attempt, an IPv6 address the network doesn't carry,
            // and drop the blocked IPv4 handshake before it. The other routes try IPv4 first.
            "ConnectException", "Failed to connect",
        ).any { text.contains(it, ignoreCase = true) }
    }
}

/** TLS over a socket whose first TLS record (the ClientHello) is split; see [BookServerConnection]. */
private object SplitHelloSocketFactory : SSLSocketFactory() {
    /**
     * Conscrypt in engine mode: it writes TLS through the socket's Java output stream, where the
     * ClientHello can be split. (The platform's default TLS writes straight to the file descriptor.)
     * Certificates are still checked against the system's trusted CAs.
     */
    private val tls: SSLSocketFactory by lazy {
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
        SSLContext.getInstance("TLS", Conscrypt.newProvider())
            .apply { init(null, trust.trustManagers, null) }
            .socketFactory
            .also { Conscrypt.setUseEngineSocket(it, true) }
    }

    override fun getDefaultCipherSuites(): Array<String> = tls.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = tls.supportedCipherSuites

    // HttpsURLConnection connects the plain socket itself and hands it over here.
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        s.tcpNoDelay = true
        return tls.createSocket(SplittingWrapper(s), host, port, autoClose)
    }

    override fun createSocket(host: String, port: Int): Socket = layered(host, InetSocketAddress(host, port))
    override fun createSocket(host: String, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        layered(host, InetSocketAddress(host, port))
    override fun createSocket(host: InetAddress, port: Int): Socket = layered(host.hostName, InetSocketAddress(host, port))
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        layered(address.hostName, InetSocketAddress(address, port))
    override fun createSocket(): Socket = SplittingSocket()

    private fun layered(host: String, address: InetSocketAddress): Socket {
        val raw = SplittingSocket()
        raw.tcpNoDelay = true
        raw.connect(address, 15_000)
        return tls.createSocket(raw, host, address.port, true)
    }
}

/** A plain socket whose first write, when it is a TLS handshake record, goes out in pieces. */
private class SplittingSocket : Socket() {
    private var stream: OutputStream? = null

    override fun getOutputStream(): OutputStream =
        stream ?: SplitFirstRecordStream(super.getOutputStream()).also { stream = it }
}

private class SplitFirstRecordStream(out: OutputStream) : FilterOutputStream(out) {
    private var done = false

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (done) {
            out.write(b, off, len)
            return
        }
        done = true
        // TLS record: type 0x16 (handshake), version (2 bytes), length (2 bytes), body.
        val isHandshake = len > 5 && b[off] == 0x16.toByte()
        val bodyLength = if (isHandshake) ((b[off + 3].toInt() and 0xFF) shl 8) or (b[off + 4].toInt() and 0xFF) else 0
        if (!isHandshake || bodyLength + 5 > len || bodyLength < 8) {
            out.write(b, off, len)
            return
        }
        // Two records: the cut lands early, before the server name extension.
        val first = minOf(bodyLength / 2, 40).coerceAtLeast(1)
        val body = off + 5
        fun record(start: Int, size: Int) = byteArrayOf(
            0x16, b[off + 1], b[off + 2], (size shr 8).toByte(), size.toByte()
        ) + b.copyOfRange(start, start + size)
        val one = record(body, first)
        val two = record(body + first, bodyLength - first)
        // And separate TCP segments (the socket has TCP_NODELAY): a few bytes, then the rest.
        out.write(one, 0, 3); out.flush()
        out.write(one, 3, one.size - 3); out.flush()
        out.write(two); out.flush()
        val rest = len - (bodyLength + 5)
        if (rest > 0) out.write(b, off + bodyLength + 5, rest)
    }

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
}

/** Wraps a connected socket so that its first TLS record is split (see [SplitFirstRecordStream]). */
private class SplittingWrapper(private val s: Socket) : Socket() {
    private val output by lazy { SplitFirstRecordStream(s.getOutputStream()) }

    override fun getOutputStream(): OutputStream = output
    override fun getInputStream(): java.io.InputStream = s.getInputStream()
    override fun connect(endpoint: java.net.SocketAddress?) = s.connect(endpoint)
    override fun connect(endpoint: java.net.SocketAddress?, timeout: Int) = s.connect(endpoint, timeout)
    override fun close() = s.close()
    override fun isConnected(): Boolean = s.isConnected
    override fun isClosed(): Boolean = s.isClosed
    override fun isBound(): Boolean = s.isBound
    override fun isInputShutdown(): Boolean = s.isInputShutdown
    override fun isOutputShutdown(): Boolean = s.isOutputShutdown
    override fun shutdownInput() = s.shutdownInput()
    override fun shutdownOutput() = s.shutdownOutput()
    override fun getInetAddress(): InetAddress? = s.inetAddress
    override fun getLocalAddress(): InetAddress = s.localAddress
    override fun getPort(): Int = s.port
    override fun getLocalPort(): Int = s.localPort
    override fun getRemoteSocketAddress(): java.net.SocketAddress? = s.remoteSocketAddress
    override fun getLocalSocketAddress(): java.net.SocketAddress? = s.localSocketAddress
    override fun getSoTimeout(): Int = s.soTimeout
    override fun setSoTimeout(timeout: Int) { s.soTimeout = timeout }
    override fun getTcpNoDelay(): Boolean = s.tcpNoDelay
    override fun setTcpNoDelay(on: Boolean) { s.tcpNoDelay = on }
    override fun getKeepAlive(): Boolean = s.keepAlive
    override fun setKeepAlive(on: Boolean) { s.keepAlive = on }
    override fun getSendBufferSize(): Int = s.sendBufferSize
    override fun setSendBufferSize(size: Int) { s.sendBufferSize = size }
    override fun getReceiveBufferSize(): Int = s.receiveBufferSize
    override fun setReceiveBufferSize(size: Int) { s.receiveBufferSize = size }
    override fun getSoLinger(): Int = s.soLinger
    override fun setSoLinger(on: Boolean, linger: Int) = s.setSoLinger(on, linger)
    override fun toString(): String = "SplittingWrapper($s)"
}
