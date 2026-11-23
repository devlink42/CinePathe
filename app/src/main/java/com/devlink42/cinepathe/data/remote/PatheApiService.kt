package com.devlink42.cinepathe.data.remote

import com.devlink42.cinepathe.data.model.CinemaInfo
import com.devlink42.cinepathe.data.model.ShowtimeDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

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
    val days: Map<String, JsonElement> = emptyMap(),
    val bookable: Boolean = true
)

class PatheApiService(private val client: HttpClient) {
    suspend fun getAllCinemas(): List<CinemaInfo> {
        val raw: String = client.get("api/cinemas") {
            parameter("language", "fr")
        }.body()

        val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(raw)
        val items: List<JsonObject> = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                val arr = root["cinemas"] ?: root["items"] ?: root["data"]
                (arr as? JsonArray)?.mapNotNull { it as? JsonObject }
                    ?: root.values.mapNotNull { it as? JsonObject }
            }
            else -> emptyList()
        }

        fun JsonObject.str(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { k ->
                (this[k] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
            }

        return items.mapNotNull { obj ->
            val slug = obj.str("slug", "id", "code") ?: return@mapNotNull null
            val name = obj.str("name", "title", "label") ?: return@mapNotNull null
            val addrObj = obj["address"] as? JsonObject

            val street = addrObj?.str("street", "address1", "line1") ?: obj.str("street", "address") ?: ""
            val zip = addrObj?.str("zipCode", "postalCode", "zip") ?: obj.str("zipCode", "postalCode") ?: ""
            val city = addrObj?.str("city", "town") ?: obj.str("city") ?: ""

            val fullAddress = listOf(street, "$zip $city".trim())
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { city.ifBlank { "Cinéma Pathé" } }

            val numericId = listOf("vistaId", "cinemaId", "id", "code")
                .firstNotNullOfOrNull { k -> (obj[k] as? JsonPrimitive)?.intOrNull }
                ?: 0

            CinemaInfo(
                slug = slug,
                name = name,
                address = fullAddress,
                city = city,
                cinemaId = numericId
            )
        }.sortedBy { it.name }
    }

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
