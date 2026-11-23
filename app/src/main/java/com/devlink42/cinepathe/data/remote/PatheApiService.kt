package com.devlink42.cinepathe.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

// --- MODÈLES (DTO) ---

@Serializable
data class CinemaDetailsDto(
    val slug: String,
    val name: String,
    val hallCount: Int,
    val seatCount: Int,
    val services: List<CinemaServiceDto> = emptyList(),
    val prices: List<PriceDto> = emptyList()
)

@Serializable
data class CinemaServiceDto(
    val label: String,
    val slug: String
)

@Serializable
data class PriceDto(
    val label: String,
    val price: Int // En centimes (ex: 1350 = 13,50€)
)

@Serializable
data class ShowDetailsDto(
    val slug: String,
    val title: String,
    val duration: Int,
    val genres: List<String> = emptyList(),
    val directors: String? = null,
    val actors: String? = null,
    val synopsis: String? = null,
    val posterPath: MediaPathsDto? = null,
    val backgroundPath: MediaPathsDto? = null
)

@Serializable
data class MediaPathsDto(
    val lg: String? = null,
    val md: String? = null,
    val sm: String? = null
)

// --- SERVICE API ---

class PatheApiService(private val client: HttpClient) {
    suspend fun getCinemaDetails(cinemaSlug: String): CinemaDetailsDto {
        return client.get("api/cinema/$cinemaSlug") {
            parameter("language", "fr")
        }.body()
    }

    suspend fun getShowDetails(showSlug: String): ShowDetailsDto {
        return client.get("api/show/$showSlug") {
            parameter("language", "fr")
        }.body()
    }

    suspend fun getShowtimes(
        showSlug: String,
        cinemaSlug: String,
        date: String // Format "2026-11-22"
    ): List<ShowtimeDto> {
        return client.get("api/show/$showSlug/showtimes/$cinemaSlug/$date") {
            parameter("language", "fr")
        }.body()
    }
}
