package com.devlink42.cinepathe.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class TicketDto(
    val bookingId: String,
    val movieTitle: String,
    val moviePoster: String? = null,
    val cinemaName: String,
    val startTime: String,
    val auditoriumName: String,
    val seats: List<String> = emptyList(),
    val qrCodeContent: String,
    val bookingReference: String = "",
    val isPast: Boolean = false
)

@Serializable
data class CinePassCard(
    val cardNumber: String,
    val holderName: String,
    val label: String,
    val expirationDate: String = "",
    val status: String = "ACTIVE",
    val photoUrl: String? = null
)

class PatheAccountService(
    private val client: HttpClient,
    private val authManager: PatheAuthManager
) {
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.str(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> this[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } }

    /**
     * Récupère les billets depuis https://secure.pathe.fr/s-order/1.0.0/tickets
     */
    suspend fun getUpcomingTickets(): List<TicketDto> {
        val raw: String = client.get("https://secure.pathe.fr/s-order/1.0.0/tickets") {
            header("Accept", "application/json")
        }.body()
        Log.d("PatheAccountRaw", "TICKETS: ${raw.take(2500)}")

        val root = jsonParser.parseToJsonElement(raw)
        val items: List<JsonObject> = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                val arr = root["tickets"] ?: root["orders"] ?: root["items"] ?: root["data"]
                (arr as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
            }
            else -> emptyList()
        }

        return items.mapIndexed { idx, obj ->
            val showObj = obj["show"] as? JsonObject
            val sessionObj = obj["session"] as? JsonObject ?: obj["showtime"] as? JsonObject
            val cinemaObj = obj["cinema"] as? JsonObject

            val title = obj.str("movieTitle", "title", "showTitle")
                ?: showObj?.str("title", "name")
                ?: "Séance Pathé"

            val poster = obj.str("poster", "moviePoster", "picture")
                ?: showObj?.str("poster", "posterUrl", "picture")

            val cinema = obj.str("cinemaName")
                ?: cinemaObj?.str("name", "title")
                ?: "Pathé Aubière"

            val rawDateTime = obj.str("date", "startTime", "showtimeDate")
                ?: sessionObj?.str("time", "startTime", "date")
                ?: ""

            val datePart = rawDateTime.substringBefore("T").substringBefore(" ").ifBlank { "À venir" }
            val timePart = rawDateTime.substringAfter("T", "").substringAfter(" ", "").take(5)

            val room = obj.str("room", "auditorium", "screen")
                ?: sessionObj?.str("room", "auditorium")
                ?: "Salle"

            val seatsList = (obj["seats"] as? JsonArray)?.mapNotNull { el ->
                when (el) {
                    is JsonObject -> el.str("name", "label", "seatNumber")
                    else -> el.jsonPrimitive.contentOrNull
                }
            } ?: emptyList()

            val qr = obj.str("qrCode", "qrPayload", "barcode", "code", "reference", "orderId", "id")
                ?: "PATHE-$idx"

            TicketDto(
                bookingId = obj.str("id", "orderId", "reference") ?: "ticket-$idx",
                movieTitle = title,
                moviePoster = poster,
                cinemaName = cinema,
                startTime = rawDateTime.ifBlank { "À venir" },
                auditoriumName = room,
                seats = seatsList,
                qrCodeContent = qr,
                bookingReference = obj.str("reference", "orderNumber", "code", "id") ?: qr,
                isPast = false
            )
        }
    }

    /**
     * Récupère les cartes CinéPass / Fidélité depuis https://c.pathe.fr/api/v1/accounts/me/cards
     */
    suspend fun fetchAndCacheCinePass(): CinePassCard? {
        val rawCards: String = client.get("https://c.pathe.fr/api/v1/accounts/me/cards?language=fr") {
            header("Accept", "application/json")
        }.body()
        Log.d("PatheAccountRaw", "CARDS: ${rawCards.take(2500)}")

        val root = jsonParser.parseToJsonElement(rawCards)
        val cardsList: List<JsonObject> = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                val arr = root["cards"] ?: root["items"] ?: root["data"]
                (arr as? JsonArray)?.mapNotNull { it as? JsonObject } ?: listOf(root)
            }
            else -> emptyList()
        }

        val activeObj = cardsList.firstOrNull { obj ->
            val type = obj.str("type", "cardType", "label", "name").orEmpty()
            type.contains("pass", ignoreCase = true) || type.contains("cine", ignoreCase = true)
        } ?: cardsList.firstOrNull() ?: return null

        val number = activeObj.str("cardNumber", "number", "code", "barcode", "id") ?: return null
        val holder = activeObj.str("holderName", "fullName", "ownerName", "firstName")
            ?: "Membre CinéPass"

        val typeLabel = activeObj.str("label", "name", "type", "cardType") ?: "CinéPass"
        val validUntil = activeObj.str("expirationDate", "validUntil", "endDate", "expiryDate") ?: ""
        val status = activeObj.str("status", "state") ?: "ACTIVE"
        val pictureId = activeObj.str("picture", "pictureId", "photo")
        val photoUrl = pictureId?.let {
            if (it.startsWith("http")) it else "https://c.pathe.fr/api/v1/accounts/me/cards/picture/$it.jpg?language=fr"
        }

        return CinePassCard(
            cardNumber = number,
            holderName = holder,
            label = typeLabel,
            expirationDate = validUntil,
            status = status,
            photoUrl = photoUrl
        )
    }
}
