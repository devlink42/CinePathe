package com.devlink42.cinepathe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

val PatheGold = Color(0xFFFFB800)
val DarkSurface = Color(0xFF141416)
val CardSurface = Color(0xFF1E1E22)

data class MovieUiModel(
    val slug: String,
    val title: String,
    val durationMinutes: Int,
    val posterUrl: String,
    val showtimes: List<ShowtimeUiModel>
)

data class ShowtimeUiModel(
    val id: String,            // ex: "148835"
    val refCmd: String,        // ex: "https://pathe.fr"
    val timeFormatted: String, // "10:15"
    val endFormatted: String,  // "13:13"
    val version: String,       // "VF" ou "VOST"
    val formatTag: String?,    // "IMAX", "4DX", null
    val auditorium: String     // "Salle 4"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CinemaScheduleScreen(
    cinemaName: String = "Pathé Aubière",
    dates: List<String>, // ex: ["Aujourd'hui", "Dim. 22", "Lun. 23"...]
    selectedDateIndex: Int,
    onDateSelected: (Int) -> Unit,
    movies: List<MovieUiModel>,
    onShowtimeClick: (MovieUiModel, ShowtimeUiModel) -> Unit,
    onOpenTicketsClick: () -> Unit
) {
    var activeFilter by remember { mutableStateOf<String?>(null) }
    val filters = listOf("VF", "IMAX", "4DX", "VOST")

    Scaffold(
        containerColor = DarkSurface,
        topBar = {
            TopAppBar(
                title = { Text(cinemaName, fontWeight = FontWeight.Bold, color = Color.White) },
                actions = {
                    // Accès direct en 1 tap aux billets & carte CinéPass
                    FilledTonalButton(
                        onClick = onOpenTicketsClick,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = PatheGold)
                    ) {
                        Text("Billets / CinéPass", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 1. Sélecteur de dates horizontal
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dates.size) { index ->
                    val isSelected = index == selectedDateIndex
                    FilterChip(
                        selected = isSelected,
                        onClick = { onDateSelected(index) },
                        label = { Text(dates[index]) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PatheGold,
                            selectedLabelColor = Color.Black,
                            labelColor = Color.White
                        )
                    )
                }
            }

            // 2. Filtres rapides (VOST / IMAX / 4DX)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { filter ->
                    val selected = activeFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { activeFilter = if (selected) null else filter },
                        label = { Text(filter) }
                    )
                }
            }

            // 3. Liste des films et horaires directs
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(movies, key = { it.slug }) { movie ->
                    val filteredShowtimes = movie.showtimes.filter { st ->
                        activeFilter == null ||
                                st.version.equals(activeFilter, ignoreCase = true) ||
                                st.formatTag.equals(activeFilter, ignoreCase = true)
                    }

                    if (filteredShowtimes.isNotEmpty()) {
                        MovieCard(
                            movie = movie,
                            showtimes = filteredShowtimes,
                            onShowtimeClick = { onShowtimeClick(movie, it) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovieCard(
    movie: MovieUiModel,
    showtimes: List<ShowtimeUiModel>,
    onShowtimeClick: (ShowtimeUiModel) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(64.dp)
                        .height(92.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${movie.durationMinutes / 60}h${(movie.durationMinutes % 60).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Grille d'horaires cliquables directement
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                showtimes.forEach { st ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onShowtimeClick(st) },
                        color = Color(0xFF2A2A30)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = st.timeFormatted,
                                fontWeight = FontWeight.Bold,
                                color = PatheGold
                            )
                            Text(
                                text = listOfNotNull(st.version, st.formatTag).joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }
        }
    }
}
