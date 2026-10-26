package com.shamela.library.data.remote.shamela

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** shamela.ws answered with a Cloudflare check; the user has to pass it in [ShamelaVerifyActivity]. */
class ShamelaChallengeException : IOException("shamela.ws asks for a browser check")

/** The page (book, category) doesn't exist on shamela.ws. */
class ShamelaNotFoundException(url: String) : IOException("not found: $url")

/**
 * GET requests to shamela.ws. Requests carry the WebView's User-Agent and cookies, so a Cloudflare
 * check the user passed in the WebView ([ShamelaVerifyActivity]) also covers these requests. A check
 * is never solved here: it surfaces as [ShamelaChallengeException].
 */
object ShamelaHttp {
    private const val TAG = "ShamelaHttp"
    const val BASE = "https://shamela.ws"
    private const val FALLBACK_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    @Volatile
    private var appContext: Context? = null

    /** The WebView's User-Agent; looked up on first use, off the main thread (it reads disk). */
    private val userAgent: String by lazy {
        try {
            appContext?.let { WebSettings.getDefaultUserAgent(it) } ?: FALLBACK_UA
        } catch (e: Exception) {
            Log.w(TAG, "no WebView user agent: ${e.message}")
            FALLBACK_UA
        }
    }

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** Fetches [url] as text, retrying network hiccups a few times. */
    suspend fun get(url: String, attempts: Int = 3): String {
        var last: IOException? = null
        repeat(attempts) { attempt ->
            try {
                return fetch(url)
            } catch (e: ShamelaChallengeException) {
                throw e
            } catch (e: ShamelaNotFoundException) {
                throw e
            } catch (e: IOException) {
                last = e
                Log.w(TAG, "GET $url failed (${attempt + 1}/$attempts): ${e.message}")
                if (attempt < attempts - 1) delay(1000L * (attempt + 1))
            }
        }
        throw last ?: IOException("GET $url failed")
    }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept-Language", "ar,en;q=0.8")
            cookies()?.let { connection.setRequestProperty("Cookie", it) }
            val code = connection.responseCode
            if (code == 403 || code == 503) {
                val body = connection.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                if (looksLikeChallenge(body) || code == 403) throw ShamelaChallengeException()
                throw IOException("HTTP $code")
            }
            if (code == 404) throw ShamelaNotFoundException(url)
            if (code !in 200..299) throw IOException("HTTP $code")
            val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            if (looksLikeChallenge(body)) throw ShamelaChallengeException()
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun cookies(): String? = try {
        CookieManager.getInstance().getCookie(BASE)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null // no WebView provider (e.g. while it updates)
    }

    /**
     * Cloudflare's interstitial. Normal pages also load `/cdn-cgi/challenge-platform/...` (bot
     * telemetry), so that alone doesn't count.
     */
    internal fun looksLikeChallenge(body: String): Boolean =
        body.contains("cf_chl_opt") ||
            body.contains("cf-chl-", ignoreCase = true) ||
            body.contains("challenge-form") ||
            body.contains("<title>Just a moment", ignoreCase = true)
}
