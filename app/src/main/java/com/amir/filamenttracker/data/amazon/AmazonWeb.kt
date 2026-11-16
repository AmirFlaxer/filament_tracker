package com.amir.filamenttracker.data.amazon

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.amir.filamenttracker.domain.amazon.acceptedTrackLinks
import com.amir.filamenttracker.domain.amazon.isAmazonUrl
import com.amir.filamenttracker.domain.amazon.isSignInUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray

const val ORDER_HISTORY_URL = "https://www.amazon.com/gp/css/order-history"

fun orderDetailsUrl(orderNumber: String): String =
    "https://www.amazon.com/your-orders/order-details?orderID=$orderNumber"

sealed interface PageRead {
    data class Text(val text: String) : PageRead
    data object SignInNeeded : PageRead

    /** A page finished loading but never showed what was expected (captcha, robot check, server error, changed layout). */
    data object Unexpected : PageRead

    /** [reason] is Hebrew and shown to the user. */
    data class Failed(val reason: String) : PageRead
}

internal const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

private const val READ_TIMEOUT_MS = 15_000L
private const val POLL_MS = 500L
private const val STABLE_POLLS = 3
private const val MSG_TIMEOUT = "לא הצלחתי לקרוא את הדף מאמזון"
private const val MSG_OFFLINE = "אין חיבור לאינטרנט"

private const val TRACK_LINKS_JS =
    "JSON.stringify(Array.from(document.querySelectorAll('a'))" +
        ".filter(function(a){return a.innerText.trim()==='Track package';})" +
        ".map(function(a){return a.href;}))"

/**
 * Reads Amazon pages with a hidden WebView that shares cookies with the visible sign-in WebView.
 * All WebView work runs on the main thread; one read at a time.
 */
@Singleton
class AmazonWeb @Inject constructor(@ApplicationContext private val context: Context) {

    private val mutex = Mutex()
    private var webView: WebView? = null

    /** Per-load state, written by the WebViewClient and read by the polling loop, both on the main thread. */
    private class LoadState {
        var finishedUrl: String? = null
        var networkError = false
        var serverError = false
    }

    suspend fun readPage(url: String, ready: (String) -> Boolean): PageRead =
        load(url) { view, _ ->
            val text = bodyText(view)
            if (ready(text)) PageRead.Text(text) else null
        }

    /**
     * Amazon links of the "Track package" buttons on the details page of [orderNumber]. Links count only
     * when the finished page really is that order's details page. The read settles once the page text
     * is unchanged for [STABLE_POLLS] polls, so an order without a shipment costs about two seconds.
     */
    suspend fun trackPackageLinks(orderNumber: String): List<String> {
        var lastText = ""
        var stable = 0
        val result = load(orderDetailsUrl(orderNumber)) { view, finishedUrl ->
            val text = bodyText(view)
            if (text.isBlank()) return@load null
            val accepted = acceptedTrackLinks(finishedUrl, text, orderNumber, trackLinks(view))
            if (accepted.isNotEmpty()) return@load PageRead.Text(accepted.joinToString("\n"))
            stable = if (text == lastText) stable + 1 else 0
            lastText = text
            if (stable >= STABLE_POLLS) PageRead.Text("") else null
        }
        return (result as? PageRead.Text)?.text?.lines()?.filter { it.isNotBlank() }.orEmpty()
    }

    fun signOut() {
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies(null)
        cookies.flush()
    }

    private suspend fun load(url: String, poll: suspend (WebView, String) -> PageRead?): PageRead {
        if (!isAmazonUrl(url)) return PageRead.Failed(MSG_TIMEOUT) // never navigate the hidden view off Amazon
        return mutex.withLock {
            withContext(Dispatchers.Main) {
                val state = LoadState()
                val view = obtainWebView()
                view.stopLoading()
                // A fresh client per load: callbacks of an earlier load write to its own, abandoned state.
                view.webViewClient = clientFor(state)
                view.loadUrl(url)
                val outcome = withTimeoutOrNull(READ_TIMEOUT_MS) {
                    var result: PageRead? = null
                    while (result == null) {
                        delay(POLL_MS)
                        val finished = state.finishedUrl
                        result = when {
                            state.networkError -> PageRead.Failed(MSG_OFFLINE)
                            finished == null -> null
                            isAmazonUrl(finished) && isSignInUrl(finished) -> PageRead.SignInNeeded
                            state.serverError -> PageRead.Unexpected
                            else -> poll(view, finished)
                        }
                    }
                    result
                }
                view.stopLoading()
                // A finished page that never became readable is worth showing to the owner.
                outcome ?: if (state.finishedUrl != null) PageRead.Unexpected else PageRead.Failed(MSG_TIMEOUT)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun obtainWebView(): WebView = webView ?: WebView(context).also {
        it.settings.javaScriptEnabled = true
        it.settings.domStorageEnabled = true
        it.settings.allowFileAccess = false
        it.settings.allowContentAccess = false
        it.settings.userAgentString = DESKTOP_USER_AGENT
        CookieManager.getInstance().setAcceptCookie(true)
        webView = it
    }

    private fun clientFor(state: LoadState) = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.isForMainFrame && !isAmazonUrl(request.url.toString())

        override fun onPageFinished(view: WebView, url: String) {
            state.finishedUrl = url
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) state.networkError = true
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (request.isForMainFrame && errorResponse.statusCode >= 500) state.serverError = true
        }
    }

    private suspend fun bodyText(view: WebView): String =
        evaluate(view, "document.body ? document.body.innerText : ''")

    private suspend fun trackLinks(view: WebView): List<String> {
        val json = evaluate(view, TRACK_LINKS_JS)
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return List(array.length()) { array.getString(it) }
    }

    /** Runs [script] and returns its string result; a callback arriving after cancellation or twice is ignored. */
    private suspend fun evaluate(view: WebView, script: String): String =
        suspendCancellableCoroutine { cont ->
            val done = AtomicBoolean(false)
            view.evaluateJavascript(script) { raw ->
                if (!done.compareAndSet(false, true)) return@evaluateJavascript
                val text = try {
                    JSONArray("[$raw]").getString(0)
                } catch (_: Exception) {
                    ""
                }
                if (cont.isActive) cont.resume(text)
            }
        }
}
