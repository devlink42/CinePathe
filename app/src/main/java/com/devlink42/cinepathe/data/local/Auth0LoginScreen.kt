package com.devlink42.cinepathe.data.local

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Auth0LoginScreen(
    authManager: PatheAuthManager,
    onLoginSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isLoadingPage by remember { mutableStateOf(true) }
    var isExchangingToken by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Génère le couple PKCE une seule fois pour cette session de connexion
    val (codeVerifier, codeChallenge) = remember { authManager.createPkceChallenge() }
    val authorizeUrl = remember { authManager.buildAuthorizeUrl(codeChallenge, silent = false) }

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
            if (!isExchangingToken) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        val cookieManager = CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                        }

                        WebView(context).apply {
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                // Évite le blocage Auth0 / Akamai lié au flag "wv"
                                userAgentString =
                                    "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoadingPage = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoadingPage = false
                                    // Sauvegarde sur disque les cookies SSO Auth0 à chaque étape
                                    cookieManager.flush()
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url ?: return false
                                    if (url.toString().startsWith(PatheAuthManager.REDIRECT_URI)) {
                                        val code = url.getQueryParameter("code")
                                        val error = url.getQueryParameter("error_description")

                                        if (!code.isNullOrBlank()) {
                                            cookieManager.flush()
                                            isExchangingToken = true
                                            coroutineScope.launch {
                                                val ok = runCatching {
                                                    authManager.exchangeCodeForTokens(code, codeVerifier)
                                                }.getOrDefault(false)

                                                if (ok) {
                                                    onLoginSuccess()
                                                } else {
                                                    isExchangingToken = false
                                                    errorMessage = "Échec de récupération du jeton Pathé."
                                                }
                                            }
                                        } else {
                                            errorMessage = error ?: "Connexion annulée."
                                        }
                                        return true // Bloque le chargement de la page /callback
                                    }
                                    return false
                                }
                            }

                            loadUrl(authorizeUrl)
                        }
                    }
                )
            }

            // Indicateur de chargement fluide
            if (isLoadingPage || isExchangingToken) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(CardSurface.copy(alpha = 0.9f))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = PatheGold)
                    if (isExchangingToken) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Activation de ton CinéPass...", color = Color.White)
                    }
                }
            }

            errorMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(msg)
                }
            }
        }
    }
}
