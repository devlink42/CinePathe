package com.devlink42.cinepathe.data.remote

import com.devlink42.cinepathe.data.model.ShowtimeDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

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
    val price: Int
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

@Serializable
data class CinemaShowItemDto(
    val slug: String,
    val title: String? = null
)

@Serializable
data class CinemaShowsResponseDto(
    val shows: Map<String, CinemaShowEntryDto> = emptyMap()
)

@Serializable
data class CinemaShowEntryDto(
    val days: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    val bookable: Boolean = true
)

class PatheApiService(private val client: HttpClient) {
    suspend fun getCinemaShows(cinemaSlug: String): CinemaShowsResponseDto {
        return client.get("api/cinema/$cinemaSlug/shows") {
            parameter("language", "fr")
        }.body()
    }

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
        date: String
    ): List<ShowtimeDto> {
        return client.get("api/show/$showSlug/showtimes/$cinemaSlug/$date") {
            parameter("language", "fr")
        }.body()
    }
}
