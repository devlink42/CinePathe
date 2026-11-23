package com.devlink42.cinepathe.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BookingOrderDto(
    val orderId: String,
    val cinemaId: Int,
    val orderState: String,
    val bookingState: String,
    val numbered: Boolean = true,
    val screenNames: Map<String, String> = emptyMap(),
    val amount: Int = 0,
    val maps: List<SeatMapDto> = emptyList(), // <-- Le plan de salle complet
    val selectionSetting: SelectionSettingDto,
    val selection: CurrentSelectionDto
)

@Serializable
data class SeatMapDto(
    val showingId: Int? = null,
    val sections: List<SeatSectionDto> = emptyList(),
    val rows: List<SeatRowDto> = emptyList() // Selon la configuration de la salle, les rangées sont soit à la racine, soit dans sections
)

@Serializable
data class SeatSectionDto(
    val sectionId: Int = 1,
    val rows: List<SeatRowDto> = emptyList()
)

@Serializable
data class SeatRowDto(
    val rowIndex: Int,
    val rowName: String? = null,       // ex: "J", "K" (vide si allée transversale)
    val seats: List<SeatItemDto> = emptyList()
)

@Serializable
data class SeatItemDto(
    val seatIndex: Int,                // Colonne exacte dans la matrice (inclut les espaces d'allées)
    val seatName: String? = null,      // ex: "1", "2", "14"
    val seatType: String = "STD",      // "STD" (Standard), "WCH" (PMR), "COMP" (Accompagnant), "EMPTY"
    val status: String = "AVAILABLE",  // "AVAILABLE", "SOLD", "RESERVED", "UNAVAILABLE", "EMPTY"
    val seatSide: String? = null,
    val section: Int = 1
)
