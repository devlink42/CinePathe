package com.devlink42.cinepathe.data.remote

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object PatheNetworkClient {
    private val jsonConfig = Json {
        ignoreUnknownKeys = true // Indispensable : ignore les champs inutiles des API
        isLenient = true
        coerceInputValues = true
    }

    val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(jsonConfig)
        }

        // Conserve automatiquement les cookies de session en mémoire
        install(HttpCookies)

        // Configuration des en-têtes par défaut pour simuler un navigateur standard
        defaultRequest {
            url("https://pathe.fr")
            header("Accept", "application/json, text/plain, */*")
            header("Accept-Language", "fr-FR,fr;q=0.9")
            header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
            )
        }

        // Dans la configuration de ton HttpClient Ktor :
        client.plugin(HttpSend).intercept { request ->
            // N'ajoute pas le Bearer sur les routes Auth0 elles-mêmes
            if (!request.url.host.contains("login.pathe.me")) {
                if (authManager.isTokenExpired()) {
                    authManager.refreshTokenSilently()
                }
                authManager.getAccessToken()?.let { token ->
                    request.header(HttpHeaders.Authorization, "Bearer $token")
                }
            }

            val originalCall = execute(request)

            // Si le serveur rejette quand même le jeton (401), on rafraîchit et on rejoue 1 fois
            if (originalCall.response.status == HttpStatusCode.Unauthorized) {
                if (authManager.refreshTokenSilently()) {
                    authManager.getAccessToken()?.let { newToken ->
                        request.headers.remove(HttpHeaders.Authorization)
                        request.header(HttpHeaders.Authorization, "Bearer $newToken")
                    }
                    execute(request)
                } else {
                    originalCall
                }
            } else {
                originalCall
            }
        }
    }
}

fun HttpClient.installPatheAuth(
    authManager: PatheAuthManager,
    onSilentRefreshNeeded: suspend () -> Boolean
) {
    plugin(HttpSend).intercept { request ->
        var token = authManager.getValidAccessToken()
        if (token == null && onSilentRefreshNeeded()) {
            token = authManager.getValidAccessToken()
        }

        if (token != null && request.url.host.endsWith("pathe.fr")) {
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

