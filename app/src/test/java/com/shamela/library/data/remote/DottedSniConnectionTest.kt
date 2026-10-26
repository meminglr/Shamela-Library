package com.shamela.library.data.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URL
import java.net.URLEncoder

/**
 * Live tests against the book server (R2), run only with SHAMELA_LIVE_NET=1 since they need the
 * network. On a network that filters "r2.dev" by SNI they show the trailing-dot route gets through.
 */
class DottedSniConnectionTest {

    private val host = "pub-c47076096099459980e9694959ee7ea3.r2.dev"
    private val path = "/" + listOf("الرقاق والآداب والأذكار", "الأربعون النووية.epub")
        .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun live() = assumeTrue(System.getenv("SHAMELA_LIVE_NET") == "1")

    private fun open(method: String, configure: DottedSniConnection.() -> Unit = {}) =
        DottedSniConnection(URL("https://$host$path")).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            configure()
        }

    @Test
    fun `HEAD gives the file size`() {
        live()
        val connection = open("HEAD")
        assertEquals(200, connection.responseCode)
        assertEquals(119116L, connection.contentLengthLong)
        connection.disconnect()
    }

    @Test
    fun `full GET returns the whole EPUB`() {
        live()
        val connection = open("GET") { setRequestProperty("Accept-Encoding", "identity") }
        assertEquals(200, connection.responseCode)
        val bytes = connection.inputStream.use { it.readBytes() }
        assertEquals(119116, bytes.size)
        assertArrayEquals("PK".toByteArray(), bytes.copyOfRange(0, 2)) // a zip
    }

    @Test
    fun `range request resumes from an offset`() {
        live()
        val connection = open("GET") { setRequestProperty("Range", "bytes=119000-") }
        assertEquals(206, connection.responseCode)
        assertEquals("bytes 119000-119115/119116", connection.getHeaderField("Content-Range"))
        assertEquals(116, connection.inputStream.use { it.readBytes() }.size)
    }

    @Test
    fun `missing file is a 404, not an exception`() {
        live()
        val connection = DottedSniConnection(URL("https://$host/no-such-book.epub"))
        assertEquals(404, connection.responseCode)
        connection.disconnect()
    }

    @Test
    fun `certificate names are matched like browsers do`() {
        // The book server's certificate (*.r2.dev), saved from the server.
        val certificate = java.security.cert.CertificateFactory.getInstance("X.509")
            .generateCertificate(javaClass.classLoader!!.getResourceAsStream("certs/r2dev.pem"))
            as java.security.cert.X509Certificate
        assertTrue(DottedSniConnection.matchesHost(certificate, host))
        assertTrue(DottedSniConnection.matchesHost(certificate, "$host."))
        assertTrue(DottedSniConnection.matchesHost(certificate, host.uppercase()))
        assertFalse(DottedSniConnection.matchesHost(certificate, "r2.dev"))
        assertFalse(DottedSniConnection.matchesHost(certificate, "a.$host"))
        assertFalse(DottedSniConnection.matchesHost(certificate, "example.com"))
    }
}
