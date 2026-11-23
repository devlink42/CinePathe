package com.devlink42.cinepathe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class PatheRoute(val route: String) {
    data object Schedule : PatheRoute("schedule")
    data object Seating : PatheRoute("seating")
    data object Ticket : PatheRoute("ticket")
}

@Composable
fun PatheAppNavigation(viewModel: PatheViewModel) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsState()

    val formattedDates = state.dates.mapIndexed { index, date ->
        when (index) {
            0 -> "Aujourd'hui"
            1 -> "Demain"
            else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH))
        }
    }

    NavHost(
        navController = navController,
        startDestination = PatheRoute.Schedule.route
    ) {
        // 1. ÉCRAN PRINCIPAL : SÉANCES DU CINÉMA
        composable(PatheRoute.Schedule.route) {
            CinemaScheduleScreen(
                dates = formattedDates,
                selectedDateIndex = state.selectedDateIndex,
                onDateSelected = { viewModel.loadScheduleForDate(it) },
                movies = state.movies,
                onShowtimeClick = { movie, showtime ->
                    viewModel.openShowtime(movie, showtime) {
                        navController.navigate(PatheRoute.Seating.route)
                    }
                },
                onOpenTicketsClick = {
                    if (state.confirmedTicket != null) {
                        navController.navigate(PatheRoute.Ticket.route)
                    }
                }
            )
        }

        // 2. ÉCRAN SÉLECTION DE SIÈGE (AVEC ANNULATION AUTO AU RETOUR)
        composable(PatheRoute.Seating.route) {
            BackHandler {
                viewModel.releasePendingOrderIfAny()
                navController.popBackStack()
            }

            SeatSelectionScreen(
                movieTitle = state.activeMovieTitle,
                auditoriumName = state.activeAuditorium,
                seats = state.seats,
                hasCinePass = state.hasCinePass,
                onConfirmSelection = { selectedSeats ->
                    viewModel.confirmSeatsAndBook(selectedSeats) {
                        // Remplace l'écran de salle par le billet (évite de revenir sur une salle déjà réservée)
                        navController.navigate(PatheRoute.Ticket.route) {
                            popUpTo(PatheRoute.Schedule.route)
                        }
                    }
                }
            )
        }

        // 3. ÉCRAN BILLET, QR CODE, CALENDRIER & GOOGLE WALLET
        composable(PatheRoute.Ticket.route) {
            val ticket = state.confirmedTicket ?: return@composable
            TicketConfirmationScreen(
                movieTitle = ticket.movieTitle,
                cinemaName = ticket.cinemaName,
                cinemaAddress = ticket.cinemaAddress,
                auditorium = ticket.auditorium,
                seatLabel = ticket.seatLabel,
                bookingCode = ticket.bookingCode,
                startTimeMillis = ticket.startTimeMillis,
                endTimeMillis = ticket.endTimeMillis,
                qrBitmap = ticket.qrBitmap,
                googleWalletUrl = ticket.googleWalletUrl
            )
        }
    }
}
