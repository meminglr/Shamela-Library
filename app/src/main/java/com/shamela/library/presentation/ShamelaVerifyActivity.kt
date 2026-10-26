package com.shamela.library.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.shamela.library.R
import com.shamela.library.data.remote.shamela.ShamelaHttp
import com.shamela.library.presentation.utils.BooksDownloadManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Opens shamela.ws in a WebView when the site asks for its browser check (Cloudflare), so the user
 * can pass it. The check's cookie stays in the WebView's cookie store, which [ShamelaHttp] sends
 * along; once the site shows a normal page this screen closes and failed downloads start again.
 */
class ShamelaVerifyActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var done = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.shamela_verify_title)
        webView = WebView(this)
        setContentView(webView)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            // Only for passing shamela.ws's check: links leading elsewhere don't open in this
            // JavaScript-enabled view (the check's own frames and scripts still load).
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host ?: return true
                return !(host == SHAMELA_HOST || host.endsWith(".$SHAMELA_HOST"))
            }

            override fun onPageFinished(view: WebView, url: String?) {
                // The check page reloads itself into the real one; stop when that one has loaded.
                view.evaluateJavascript("document.documentElement.outerHTML.length > 0 && " +
                    "!/Just a moment|cf_chl_opt|challenge-form/.test(document.documentElement.outerHTML)") { passed ->
                    if (passed == "true") onVerified()
                }
            }
        }
        if (savedInstanceState == null) webView.loadUrl("${ShamelaHttp.BASE}/")
        else webView.restoreState(savedInstanceState)
        Toast.makeText(this, R.string.shamela_verify_hint, Toast.LENGTH_LONG).show()
    }

    private fun onVerified() {
        if (done) return
        done = true
        CookieManager.getInstance().flush()
        _verified.tryEmit(Unit)
        BooksDownloadManager.retryFailed()
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val SHAMELA_HOST = "shamela.ws"
        private val _verified = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        /** Emits when the user passed the check, so screens can load what failed. */
        val verified: SharedFlow<Unit> = _verified.asSharedFlow()

        fun open(context: Context) {
            context.startActivity(
                Intent(context, ShamelaVerifyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
