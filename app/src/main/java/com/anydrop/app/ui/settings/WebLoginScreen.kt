package com.anydrop.app.ui.settings

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.White

/**
 * In-app login: loads [loginUrl] in a WebView so the user signs in normally, then
 * captures the resulting session cookies via CookieManager — no PC/desktop tool.
 */
@Composable
fun WebLoginScreen(
    loginUrl: String,
    cookieDomainUrl: String,
    onCancel: () -> Unit,
    onCookiesCaptured: (rawCookies: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGray)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Black)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onCancel) {
                Text("CANCEL", color = White.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            Text(
                "LOG IN, THEN TAP DONE",
                color = White.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
            Button(
                onClick = {
                    // Flush any buffered cookies to storage before reading them.
                    CookieManager.getInstance().flush()
                    val cookies = CookieManager.getInstance().getCookie(cookieDomainUrl) ?: ""
                    onCookiesCaptured(cookies)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Black)
            ) {
                Text("DONE", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(White),
            factory = { ctx ->
                WebView(ctx).apply {
                    // Ensure the view actually fills its space and paints on white
                    // (a zero-height/transparent WebView is what looked "blank").
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.WHITE)
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    with(settings) {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        // Instagram blanks embedded WebViews that don't look like a
                        // real Chrome browser. Present a genuine mobile Chrome UA and
                        // enable the browser-like features its login JS expects.
                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                        javaScriptCanOpenWindowsAutomatically = true
                        loadsImagesAutomatically = true
                        mediaPlaybackRequiresUserGesture = false
                        cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        // Let the mobile page use the WebView's own width (wide-viewport
                        // + overview mode could shrink IG's page to near-zero = blank).
                        useWideViewPort = false
                        loadWithOverviewMode = false
                        setSupportZoom(true)
                        // Some IG flows open the login in a popup/new window.
                        setSupportMultipleWindows(false)
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            android.util.Log.d("WebLogin", "Page finished: $url")
                            // Auto-detect successful login: once the session cookie
                            // appears, capture cookies automatically (no DONE tap needed).
                            CookieManager.getInstance().flush()
                            val cookies = CookieManager.getInstance().getCookie(cookieDomainUrl) ?: ""
                            if (cookies.contains("sessionid=", ignoreCase = true) &&
                                cookies.substringAfter("sessionid=", "").substringBefore(";").isNotBlank()
                            ) {
                                onCookiesCaptured(cookies)
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            android.util.Log.w(
                                "WebLogin",
                                "Load error for ${request?.url}: ${error?.errorCode} ${error?.description}"
                            )
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            errorResponse: android.webkit.WebResourceResponse?
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            android.util.Log.w(
                                "WebLogin",
                                "HTTP ${errorResponse?.statusCode} for ${request?.url}"
                            )
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(
                            msg: android.webkit.ConsoleMessage?
                        ): Boolean {
                            android.util.Log.d(
                                "WebLogin",
                                "console: ${msg?.message()} @${msg?.sourceId()}:${msg?.lineNumber()}"
                            )
                            return true
                        }
                    }
                    loadUrl(loginUrl)
                }
            }
        )
    }
}
