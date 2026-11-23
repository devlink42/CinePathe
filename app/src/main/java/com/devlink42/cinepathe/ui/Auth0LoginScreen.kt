package com.devlink42.cinepathe.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.devlink42.cinepathe.data.remote.PatheAuthManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Auth0LoginScreen(
    authManager: PatheAuthManager,
    onLoginSuccess: () -> Unit
) {
    var isLoadingPage by remember { mutableStateOf(true) }
    var hasLoggedIn by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Script qui cherche le jeton Auth0 dans localStorage / sessionStorage
    val jsCheckStorage = remember {
        """
        (function() {
            function scanStorage(storage) {
                if (!storage) return null;
                for (var i = 0; i < storage.length; i++) {
                    var key = storage.key(i);
                    if (key && key.indexOf('@@auth0spajs@@') !== -1 && key.indexOf('@@user@@') === -1) {
                        try {
                            var data = JSON.parse(storage.getItem(key));
                            if (data && data.body && data.body.access_token) {
                                return JSON.stringify({
                                    access_token: data.body.access_token,
                                    id_token: data.body.id_token || "",
                                    expires_in: data.body.expires_in || 3600
                                });
                            }
                        } catch (e) {}
                    }
                }
                return null;
            }
            var found = scanStorage(window.localStorage) || scanStorage(window.sessionStorage);
            if (found && window.AndroidAuthBridge) {
                window.AndroidAuthBridge.onTokenCaptured(found);
            }
        })();
        """.trimIndent()
    }

    // Vérifie périodiquement le stockage une fois sur pathe.fr (gère le fetch asynchrone d'Auth0)
    LaunchedEffect(webViewRef, hasLoggedIn) {
        while (!hasLoggedIn) {
            delay(1000L)
            webViewRef?.evaluateJavascript(jsCheckStorage, null)
        }
    }

    Scaffold(
        containerColor = DarkSurface,
        topBar = {
            TopAppBar(
                title = { Text("Connexion Compte Pathé", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(DarkSurface)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val cookieManager = CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                    }

                    WebView(context).apply {
                        webViewRef = this
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            userAgentString =
                                "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                        }

                        // Pont natif appelé dès que le jeton Auth0 est intercepté
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onTokenCaptured(jsonStr: String) {
                                if (hasLoggedIn) return
                                runCatching {
                                    val obj = org.json.JSONObject(jsonStr)
                                    val accessToken = obj.optString("access_token")
                                    val idToken = obj.optString("id_token")
                                    val expiresIn = obj.optLong("expires_in", 3600L)
                                    if (accessToken.isNotBlank()) {
                                        hasLoggedIn = true
                                        val expiresAtSeconds = (System.currentTimeMillis() / 1000L) + expiresIn
                                        authManager.saveTokensFromWeb(accessToken, idToken, expiresAtSeconds)
                                        post { onLoginSuccess() }
                                    }
                                }
                            }
                        }, "AndroidAuthBridge")

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoadingPage = true
                                val hookFetch = """
                                    (function() {
                                        if (window.__patheFetchHooked) return;
                                        window.__patheFetchHooked = true;
                                        var origFetch = window.fetch;
                                        window.fetch = function() {
                                            var args = arguments;
                                            var reqUrl = (typeof args[0] === 'string') ? args[0] : (args[0] && args[0].url) || '';
                                            return origFetch.apply(this, args).then(function(response) {
                                                if (reqUrl.indexOf('/oauth/token') !== -1) {
                                                    response.clone().json().then(function(data) {
                                                        if (data && data.access_token && window.AndroidAuthBridge) {
                                                            window.AndroidAuthBridge.onTokenCaptured(JSON.stringify(data));
                                                        }
                                                    }).catch(function() {});
                                                }
                                                return response;
                                            });
                                        };
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(hookFetch, null)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoadingPage = false
                                cookieManager.flush()
                                view?.evaluateJavascript(jsCheckStorage, null)
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                val url = request?.url?.toString().orEmpty()
                                if (url.contains("pathe.fr/api/") || url.contains("s.pathe.fr") || url.contains("pathe.me")) {
                                    android.util.Log.d("PatheSniffer", "${request?.method} $url")
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        loadUrl("https://www.pathe.fr")
                    }
                }
            )

            if (isLoadingPage) {
                CircularProgressIndicator(
                    color = PatheGold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
