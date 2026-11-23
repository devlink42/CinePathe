package com.devlink42.cinepathe.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom

@Serializable
data class Auth0TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("id_token") val idToken: String,
    @SerialName("expires_in") val expiresIn: Long
)

@Serializable
data class PatheJwtClaims(
    @SerialName("salesforce_icu") val clientId: String,
    @SerialName("salesforce_account_id") val accountId: String? = null,
    val email: String? = null,
    val name: String? = null
)

@Serializable
data class CinePassCardDto(
    val cardNumber: String,
    val label: String? = "CinéPass",
    val status: String? = "ACTIVE",
    val expirationDate: String? = null
)

class PatheAuthManager(context: Context, private val httpClient: HttpClient) {

    companion object {
        const val AUTH0_DOMAIN = "https://login.pathe.me"
        const val CLIENT_ID = "r30RbF2Mjy49xXOXSD3ELqzUuKTzag0L"
        const val AUDIENCE = "https://pathe.me/"
        const val REDIRECT_URI = "https://www.pathe.fr"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pathe_auth_vault",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokensFromWeb(accessToken: String, idToken: String, expiresAtSeconds: Long) {
        val expiresAtMillis = if (expiresAtSeconds > 0) {
            (expiresAtSeconds * 1000L) - 60_000L
        } else {
            System.currentTimeMillis() + 3600_000L
        }
        val claims = decodeIdToken(idToken)

        prefs.edit()
            .putString("access_token", accessToken)
            .putString("id_token", idToken)
            .putLong("expires_at", expiresAtMillis)
            .putString("client_id", claims?.clientId)
            .apply()
    }

    // --- 1. GÉNÉRATION PKCE ---

    fun createPkceChallenge(): Pair<String, String> {
        val bytes = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return verifier to challenge
    }

    fun buildAuthorizeUrl(codeChallenge: String, silent: Boolean = false): String {
        return URLBuilder("$AUTH0_DOMAIN/authorize").apply {
            parameters.append("client_id", CLIENT_ID)
            parameters.append("redirect_uri", REDIRECT_URI)
            parameters.append("audience", AUDIENCE)
            parameters.append("scope", "openid profile email")
            parameters.append("response_type", "code")
            parameters.append("code_challenge", codeChallenge)
            parameters.append("code_challenge_method", "S256")
            parameters.append("ui_locales", "fr")
            if (silent) parameters.append("prompt", "none")
        }.buildString()
    }

    // --- 2. ÉCHANGE DU CODE CONTRE LES JETONS ---

    suspend fun exchangeCodeForTokens(code: String, codeVerifier: String): Boolean {
        val response: Auth0TokenResponse = httpClient.post("$AUTH0_DOMAIN/oauth/token") {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "client_id" to CLIENT_ID,
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "code_verifier" to codeVerifier,
                    "redirect_uri" to REDIRECT_URI
                )
            )
        }.body()

        saveTokens(response)
        return true
    }

    private fun saveTokens(tokens: Auth0TokenResponse) {
        val expiresAt = System.currentTimeMillis() + (tokens.expiresIn * 1000L) - 60_000L // Marge 1 min
        val claims = decodeIdToken(tokens.idToken)

        prefs.edit()
            .putString("access_token", tokens.accessToken)
            .putString("id_token", tokens.idToken)
            .putLong("expires_at", expiresAt)
            .putString("client_id", claims?.clientId)
            .apply()
    }

    private fun decodeIdToken(jwt: String): PatheJwtClaims? = runCatching {
        val payloadBase64 = jwt.split(".")[1]
        val decoded = String(Base64.decode(payloadBase64, Base64.URL_SAFE), Charsets.UTF_8)
        json.decodeFromString<PatheJwtClaims>(decoded)
    }.getOrNull()

    fun getValidAccessToken(): String? {
        val token = prefs.getString("access_token", null) ?: return null
        val expiresAt = prefs.getLong("expires_at", 0L)
        return if (System.currentTimeMillis() < expiresAt) token else null
    }

    fun getClientId(): String? = prefs.getString("client_id", null)

    fun saveCinePassNumber(cardNumber: String) {
        prefs.edit().putString("cinepass_number", cardNumber).apply()
    }

    fun getCinePassNumber(): String? = prefs.getString("cinepass_number", null)
}
