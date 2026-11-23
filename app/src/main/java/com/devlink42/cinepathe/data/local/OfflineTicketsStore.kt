package com.devlink42.cinepathe.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.offlineDataStore by preferencesDataStore(name = "pathe_offline_vault")

@Serializable
data class CachedTicket(
    val bookingCode: String,       // ex: "WQTQZPL"
    val qrContent: String,         // Chaîne encodée pour la borne
    val movieTitle: String,
    val cinemaName: String = "Pathé Aubière",
    val cinemaAddress: String = "Avenue Lavoisier, 63170 Aubière",
    val auditorium: String,        // ex: "Salle 4"
    val seatLabel: String,         // ex: "J2"
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val googleWalletUrl: String? = null
)

@Serializable
data class CachedCinePass(
    val cardNumber: String,
    val holderName: String = "Abonné CinéPass",
    val expirationDate: String? = null
)

class OfflineTicketsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val ticketsKey = stringPreferencesKey("cached_tickets_json")
    private val cinePassKey = stringPreferencesKey("cached_cinepass_json")

    // Flux temps réel des billets valides (masque automatiquement ceux terminés depuis +3h)
    val upcomingTicketsFlow: Flow<List<CachedTicket>> = context.offlineDataStore.data.map { prefs ->
        val raw = prefs[ticketsKey] ?: return@map emptyList()
        val cutoffTime = System.currentTimeMillis() - (3 * 3600_000L)
        runCatching {
            json.decodeFromString<List<CachedTicket>>(raw)
                .filter { it.endTimeMillis > cutoffTime }
                .sortedBy { it.startTimeMillis }
        }.getOrDefault(emptyList())
    }

    val cinePassFlow: Flow<CachedCinePass?> = context.offlineDataStore.data.map { prefs ->
        val raw = prefs[cinePassKey] ?: return@map null
        runCatching { json.decodeFromString<CachedCinePass>(raw) }.getOrNull()
    }

    suspend fun saveTicket(ticket: CachedTicket) {
        context.offlineDataStore.edit { prefs ->
            val current = prefs[ticketsKey]?.let {
                runCatching { json.decodeFromString<List<CachedTicket>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()

            val updated = (current.filterNot { it.bookingCode == ticket.bookingCode } + ticket)
            prefs[ticketsKey] = json.encodeToString(updated)
        }
    }

    suspend fun saveCinePass(pass: CachedCinePass) {
        context.offlineDataStore.edit { prefs ->
            prefs[cinePassKey] = json.encodeToString(pass)
        }
    }
}
