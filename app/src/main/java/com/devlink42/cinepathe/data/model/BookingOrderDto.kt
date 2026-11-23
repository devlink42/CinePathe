package com.devlink42.cinepathe.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BookingOrderDto(
    val orderId: String,
    val cinemaId: Int,
    val orderState: String,          // "INIT"
    val bookingState: String,        // "AVAILABLE"
    val numbered: Boolean,           // true = placement numéroté
    val screenNames: Map<String, String> = emptyMap(), // {"148835": "Salle 4"}
    val amount: Int = 0,             // Total en centimes
    val maps: List<SeatMapDto> = emptyList(),
    val selectionSetting: SelectionSettingDto,
    val selection: CurrentSelectionDto
)

@Serializable
data class SelectionSettingDto(
    val rates: AvailableRatesDto
)

@Serializable
data class AvailableRatesDto(
    val default: List<TicketRateDto> = emptyList(),
    val voucher: List<TicketRateDto> = emptyList()
)

@Serializable
data class TicketRateDto(
    val code: String,
    val label: String,
    val price: Int,
    val quantity: Int = 0,
    val justification: String? = null
)

@Serializable
data class CurrentSelectionDto(
    val emailAddress: String? = null,
    val prepaidCodes: List<PrepaidCardDto> = emptyList()
)

@Serializable
data class PrepaidCardDto(
    val cardId: String,
    val subCategory: String? = null, // "CINEPASS"
    val balance: Int = 0
)
