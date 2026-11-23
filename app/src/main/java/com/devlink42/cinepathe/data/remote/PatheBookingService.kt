package com.devlink42.cinepathe.data.remote

import com.devlink42.cinepathe.data.model.BookingOrderDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class SeatAllocationRequest(
    val seats: List<SeatPayload>,
    val activeShowing: Int
)

@Serializable
data class SeatPayload(
    val rowIndex: Int,
    val rowName: String,
    val seatIndex: Int,
    val seatName: String,
    val seatSide: String? = null,
    val seatType: String = "STD",
    val section: Int = 1
)

class PatheBookingService(private val client: HttpClient) {

    suspend fun getBookingOrder(cinemaId: Int, orderId: String): BookingOrderDto {
        return client.get("https://s.pathe.fr/api/fr-FR/order/$cinemaId/$orderId/booking")
            .body()
    }

    suspend fun allocateSeats(
        cinemaId: Int,
        showingId: Int,
        orderId: String,
        seats: List<SeatPayload>
    ): Boolean {
        val response: HttpResponse = client.post(
            "https://s.pathe.fr/api/fr-FR/order/$cinemaId/$orderId/seat/allocation"
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

    suspend fun cancelOrder(cinemaId: Int, orderId: String): Boolean {
        val response: HttpResponse = client.delete(
            "https://s.pathe.fr/api/fr-FR/order/$cinemaId/$orderId"
        )
        return response.status.isSuccess()
    }
}
