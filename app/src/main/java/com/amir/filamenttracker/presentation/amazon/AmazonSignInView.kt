package com.amir.filamenttracker.presentation.amazon

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.amir.filamenttracker.data.amazon.ORDER_HISTORY_URL
import com.amir.filamenttracker.domain.amazon.isAmazonUrl
import com.amir.filamenttracker.domain.amazon.isOrderListUrl

/** A visible WebView for signing in to Amazon by hand; calls [onSignedIn] once the order list loads. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AmazonSignInView(onSignedIn: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                CookieManager.getInstance().setAcceptCookie(true)
                webViewClient = object : WebViewClient() {
                    private var signaled = false

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.isForMainFrame && !isAmazonUrl(request.url.toString())

                    override fun onPageFinished(view: WebView, url: String) {
                        if (signaled) return
                        if (isOrderListUrl(url)) {
                            signaled = true
                            CookieManager.getInstance().flush()
                            currentOnSignedIn()
                        }
                    }
                }
                loadUrl(ORDER_HISTORY_URL)
            }
        },
        onRelease = { it.destroy() },
    )
}
