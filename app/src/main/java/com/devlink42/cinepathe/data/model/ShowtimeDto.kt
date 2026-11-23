package com.devlink42.cinepathe.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ShowtimeDto(
    val time: String,                 // "2026-11-22 10:15:00"
    val endTime: String,              // "2026-11-22 13:13:00"
    val version: String,              // "vf" ou "vost"
    val tags: List<String> = emptyList(), // ["DEFAULT", "GPXP", "imax", "4dx", "pmr"]
    val status: String,               // "available"
    val isMovie: Boolean = true,
    val refCmd: String,               // "https://pathe.fr"
    val auditoriumName: String,       // "IMAX", "4DX", "4"...
    val auditoriumCapacity: String? = null,
    val specialShowtimeDetails: SpecialDetailsDto? = null
)

@Serializable
data class SpecialDetailsDto(
    val titleScreening: String? = null,
    val titleShowtimeDetails: String? = null,
    val descriptionShowtimeDetails: String? = null
)
