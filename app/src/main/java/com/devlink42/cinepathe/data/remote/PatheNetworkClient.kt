package com.devlink42.cinepathe.data.remote

import android.webkit.CookieManager
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object PatheNetworkClient {
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(jsonConfig)
        }

        defaultRequest {
            url("https://www.pathe.fr/")
            header("Accept", "application/json, text/plain, */*")
            header("Accept-Language", "fr-FR,fr;q=0.9")
            header("Origin", "https://www.pathe.fr")
            header("Referer", "https://www.pathe.fr/")
            header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            )
            // Réutilise les cookies Akamai / session générés par la WebView
            runCatching { CookieManager.getInstance().getCookie("https://www.pathe.fr") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { header(HttpHeaders.Cookie, it) }
        }
    }
}

fun HttpClient.installPatheAuth(
    authManager: PatheAuthManager,
    onSilentRefreshNeeded: suspend () -> Boolean = { false }
) {
    plugin(HttpSend).intercept { request ->
        if (request.url.host.contains("pathe.me")) {
            return@intercept execute(request)
        }

        var token = authManager.getValidAccessToken()
        if (token == null && onSilentRefreshNeeded()) {
            token = authManager.getValidAccessToken()
        }

        if (token != null && request.url.host.endsWith("pathe.fr")) {
            request.headers.remove(HttpHeaders.Authorization)
            request.headers.append(HttpHeaders.Authorization, "Bearer $token")
        }

        val call = execute(request)
        if (call.response.status == HttpStatusCode.Unauthorized && onSilentRefreshNeeded()) {
            authManager.getValidAccessToken()?.let { freshToken ->
                request.headers.remove(HttpHeaders.Authorization)
                request.headers.append(HttpHeaders.Authorization, "Bearer $freshToken")
                execute(request)
            } ?: call
        } else {
            call
        }
    }
}
