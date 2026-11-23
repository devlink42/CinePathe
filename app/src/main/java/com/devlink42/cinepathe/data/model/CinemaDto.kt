package com.devlink42.cinepathe.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CinemaInfo(
    val slug: String,
    val name: String,
    val address: String,
    val city: String = "",
    val cinemaId: Int = 0
)
