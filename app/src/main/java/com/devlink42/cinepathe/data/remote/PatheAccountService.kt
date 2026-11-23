package com.devlink42.cinepathe.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

@Serializable
data class ClientBookingDto(
    val bookingId: String,
    val movieTitle: String,
    val cinemaName: String,
    val auditoriumName: String,     // ex: "Salle 4"
    val startTime: String,          // ISO-8601
    val seats: List<String>,        // ex: ["J2"]
    val qrCodeContent: String,      // Chaîne encodée dans le QR code de la borne
    val cinePassNumber: String? = null
)

class PatheAccountService(
    private val client: HttpClient,
    private val authManager: PatheAuthManager
) {
    suspend fun getUpcomingTickets(): List<ClientBookingDto> {
        val clientId = authManager.getClientId() ?: error("Non connecté")
        // Endpoint à confirmer via un rapide dump sur pathe.fr/mon-compte/reservations
        return client.get("https://pathe.fr") {
            parameter("language", "fr")
        }.body()
    }
}

suspend fun fetchAndCacheCinePass(): CinePassCardDto? {
    val clientId = authManager.getClientId() ?: return null
    val cards: List<CinePassCardDto> = client.get("https://pathe.fr") {
        parameter("language", "fr")
    }.body()

    val activePass = cards.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }
    if (activePass != null) {
        authManager.saveCinePassNumber(activePass.cardNumber)
    }
    return activePass
}
