package com.devlink42.cinepathe.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class SeatNode(
    val id: String,         // ex: "J2"
    val rowIndex: Int,
    val colIndex: Int,
    val rowName: String,
    val seatName: String,
    val isAvailable: Boolean,
    val isPmr: Boolean = false
)

@Composable
fun SeatSelectionScreen(
    movieTitle: String,
    auditoriumName: String,
    seats: List<SeatNode>,
    hasCinePass: Boolean,
    onConfirmSelection: (List<SeatNode>) -> Unit
) {
    var selectedSeats by remember { mutableStateOf(setOf<SeatNode>()) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val maxCols = remember(seats) { (seats.maxOfOrNull { it.colIndex } ?: 10) + 1 }
    val maxRows = remember(seats) { (seats.maxOfOrNull { it.rowIndex } ?: 10) + 1 }

    Scaffold(
        containerColor = DarkSurface,
        bottomBar = {
            Surface(color = CardSurface, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (selectedSeats.isEmpty()) "Choisis ton fauteuil"
                            else "Place(s) : ${selectedSeats.joinToString(", ") { it.id }}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (hasCinePass && selectedSeats.isNotEmpty()) {
                            Text("CinéPass actif • 0,00 €", color = PatheGold, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Button(
                        enabled = selectedSeats.isNotEmpty(),
                        onClick = { onConfirmSelection(selectedSeats.toList()) },
                        colors = ButtonDefaults.buttonColors(containerColor = PatheGold)
                    ) {
                        Text("Réserver", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$movieTitle • $auditoriumName",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )

            // Zone Canvas interactive (Zoom + Pan + Tap)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.8f, 3.0f)
                            offset += pan
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .pointerInput(seats, selectedSeats) {
                            detectTapGestures { tapOffset ->
                                val paddingHorizontal = 40f
                                val cellSize = (size.width - paddingHorizontal * 2) / maxCols
                                val startY = 140f

                                val clicked = seats.find { seat ->
                                    val x = paddingHorizontal + seat.colIndex * cellSize
                                    val y = startY + seat.rowIndex * cellSize
                                    tapOffset.x in x..(x + cellSize * 0.8f) &&
                                            tapOffset.y in y..(y + cellSize * 0.8f)
                                }

                                if (clicked != null && clicked.isAvailable) {
                                    selectedSeats = if (clicked in selectedSeats) {
                                        selectedSeats - clicked
                                    } else {
                                        selectedSeats + clicked
                                    }
                                }
                            }
                        }
                ) {
                    // Dessin de l'écran incurvé en haut
                    val screenPath = Path().apply {
                        moveTo(size.width * 0.15f, 70f)
                        quadraticTo(size.width * 0.5f, 30f, size.width * 0.85f, 70f)
                    }
                    drawPath(
                        path = screenPath,
                        color = PatheGold,
                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                    )

                    val paddingHorizontal = 40f
                    val cellSize = (size.width - paddingHorizontal * 2) / maxCols
                    val seatDim = cellSize * 0.78f
                    val startY = 140f

                    seats.forEach { seat ->
                        val isSelected = seat in selectedSeats
                        val seatColor = when {
                            isSelected -> PatheGold
                            !seat.isAvailable -> Color(0xFF333338)
                            seat.isPmr -> Color(0xFF3B82F6)
                            else -> Color(0xFF6B7280)
                        }

                        drawRoundRect(
                            color = seatColor,
                            topLeft = Offset(
                                x = paddingHorizontal + seat.colIndex * cellSize,
                                y = startY + seat.rowIndex * cellSize
                            ),
                            size = Size(seatDim, seatDim),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }
                }
            }
        }
    }
}
