package com.devlink42.cinepathe.data.remote

import com.devlink42.cinepathe.data.model.BookingOrderDto
import com.devlink42.cinepathe.ui.SeatNode

data class ParsedRoomLayout(
    val seats: List<SeatNode>,
    val rowLabels: Map<Int, String> // ex: {0 -> "A", 1 -> "B", ..., 9 -> "J"}
)

fun BookingOrderDto.toRoomLayout(targetShowingId: Int? = null): ParsedRoomLayout {
    // 1. Sélectionne la map correspondant à la séance active (ou la première par défaut)
    val activeMap = maps.firstOrNull { targetShowingId == null || it.showingId == targetShowingId }
        ?: return ParsedRoomLayout(emptyList(), emptyMap())

    // 2. Récupère toutes les rangées (qu'elles soient dans sections ou directement dans activeMap)
    val allRows = if (activeMap.sections.isNotEmpty()) {
        activeMap.sections.flatMap { it.rows }
    } else {
        activeMap.rows
    }

    val seatNodes = mutableListOf<SeatNode>()
    val rowLabels = mutableMapOf<Int, String>()

    for ((rowIndex, rowName, seats) in allRows) {
        val cleanRowName = rowName?.trim().orEmpty()
        if (cleanRowName.isNotEmpty()) {
            rowLabels[rowIndex] = cleanRowName
        }

        for ((seatIndex, seatName, seatType, status) in seats) {
            val cleanSeatName = seatName?.trim().orEmpty()

            // Ignore les cases vides de la matrice (allées verticales, escaliers, espaces)
            val isRealSeat = cleanSeatName.isNotEmpty() &&
                    !seatType.equals("EMPTY", ignoreCase = true) &&
                    !status.equals("EMPTY", ignoreCase = true)

            if (!isRealSeat) continue

            val isAvailable = status.equals("AVAILABLE", ignoreCase = true) ||
                    status.equals("FREE", ignoreCase = true)

            val isPmr = seatType.equals("WCH", ignoreCase = true) ||
                    seatType.equals("COMP", ignoreCase = true)

            seatNodes.add(
                SeatNode(
                    id = "$cleanRowName$cleanSeatName", // ex: "J2"
                    rowIndex = rowIndex,
                    colIndex = seatIndex,
                    rowName = cleanRowName,
                    seatName = cleanSeatName,
                    isAvailable = isAvailable,
                    isPmr = isPmr
                )
            )
        }
    }

    return ParsedRoomLayout(
        seats = seatNodes,
        rowLabels = rowLabels
    )
}
