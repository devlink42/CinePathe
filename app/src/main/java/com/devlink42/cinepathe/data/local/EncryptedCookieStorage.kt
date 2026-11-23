package com.devlink42.cinepathe.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.cookieDataStore by preferencesDataStore(name = "pathe_encrypted_cookies")

@Serializable
data class SerializableCookie(
    val name: String,
    val value: String,
    val domain: String?,
    val path: String?,
    val secure: Boolean,
    val httpOnly: Boolean,
    val expiresTimestamp: Long?
)

class EncryptedCookieStorage(private val context: Context) : CookiesStorage {

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val encryptedCookiesKey = stringPreferencesKey("cookies_blob")

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        val now = getTimeMillis()
        loadAllCookies().filter { cookie ->
            val domainMatches = cookie.domain == null ||
                    requestUrl.host.endsWith(cookie.domain.removePrefix("."))
            val notExpired = cookie.expires == null || cookie.expires!!.timestamp > now
            domainMatches && notExpired
        }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie): Unit = mutex.withLock {
        if (cookie.name.isBlank()) return@withLock
        val current = loadAllCookies().toMutableList()
        val domain = cookie.domain ?: requestUrl.host

        current.removeAll { it.name == cookie.name && (it.domain ?: requestUrl.host) == domain }
        current.add(cookie.copy(domain = domain))
        saveAllCookies(current)
    }

    override fun close() {}

    private suspend fun loadAllCookies(): List<Cookie> {
        val encrypted = context.cookieDataStore.data.first()[encryptedCookiesKey] ?: return emptyList()
        val rawJson = CryptoHelper.decrypt(encrypted) ?: return emptyList()

        return runCatching {
            json.decodeFromString<List<SerializableCookie>>(rawJson).map {
                Cookie(
                    name = it.name,
                    value = it.value,
                    domain = it.domain,
                    path = it.path,
                    secure = it.secure,
                    httpOnly = it.httpOnly,
                    expires = it.expiresTimestamp?.let { ts -> GMTDate(ts) }
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun saveAllCookies(cookies: List<Cookie>) {
        val serializable = cookies.map {
            SerializableCookie(
                name = it.name,
                value = it.value,
                domain = it.domain,
                path = it.path,
                secure = it.secure,
                httpOnly = it.httpOnly,
                expiresTimestamp = it.expires?.timestamp
            )
        }
        val encrypted = CryptoHelper.encrypt(json.encodeToString(serializable))
        context.cookieDataStore.edit { prefs ->
            prefs[encryptedCookiesKey] = encrypted
        }
    }
}

