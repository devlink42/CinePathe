package com.devlink42.cinepathe.data.remote

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

// --- MODÈLES DE REQUÊTE ---

@Serializable
data class SeatAllocationRequest(
    val seats: List<SeatPayload>,
    val activeShowing: Int
)

@Serializable
data class SeatPayload(
    val rowIndex: Int,
    val rowName: String,       // ex: "J"
    val seatIndex: Int,
    val seatName: String,      // ex: "2"
    val seatSide: String? = null,
    val seatType: String = "STD", // "STD", "WCH" (PMR), "COMP" (Accompagnant PMR)
    val section: Int = 1
)

// --- SERVICE DE RÉSERVATION ---

class PatheBookingService(private val client: HttpClient) {

    suspend fun allocateSeats(
        cinemaId: Int,       // ex: 3337 (Pathé Aubière)
        showingId: Int,      // ex: 148835
        orderId: String,     // ex: "DF64F5E4-061C-4EA2-910D-D786663D4FD0"
        seats: List<SeatPayload>
    ): Boolean {
        val response: HttpResponse = client.post(
            "https://pathe.fr"
        ) {
            parameter("pos", "c2VhdGluZw==") // "seating" en Base64
            contentType(ContentType.Application.Json)
            setBody(
                SeatAllocationRequest(
                    seats = seats,
                    activeShowing = showingId
                )
            )
        }
        return response.status.isSuccess()
    }
}
