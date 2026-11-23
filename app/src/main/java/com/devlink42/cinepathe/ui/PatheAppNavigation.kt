package com.devlink42.cinepathe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.devlink42.cinepathe.data.remote.PatheAuthManager
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class PatheRoute(val route: String) {
    data object Login : PatheRoute("login")
    data object Schedule : PatheRoute("schedule")
    data object Seating : PatheRoute("seating")
    data object Ticket : PatheRoute("ticket")
    data object Wallet : PatheRoute("wallet")
}

@Composable
fun PatheAppNavigation(
    viewModel: PatheViewModel,
    authManager: PatheAuthManager,
    startRoute: String
) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsState()
    val upcomingTickets by viewModel.upcomingTickets.collectAsState()
    val cinePass by viewModel.cinePass.collectAsState()

    val formattedDates = state.dates.mapIndexed { index, date ->
        when (index) {
            0 -> "Aujourd'hui"
            1 -> "Demain"
            else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH))
        }
    }

    NavHost(
        navController = navController,
        startDestination = startRoute
    ) {
        // 0. CONNEXION AUTH0 PKCE
        composable(PatheRoute.Login.route) {
            Auth0LoginScreen(
                authManager = authManager,
                onLoginSuccess = {
                    viewModel.syncUserAccount()
                    viewModel.loadScheduleForDate(0)
                    navController.navigate(PatheRoute.Schedule.route) {
                        popUpTo(PatheRoute.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // 1. SÉANCES DU CINÉMA
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
                    navController.navigate(PatheRoute.Wallet.route)
                }
            )
        }

        // 2. SÉLECTION DE SIÈGE
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
                        navController.navigate(PatheRoute.Ticket.route) {
                            popUpTo(PatheRoute.Schedule.route)
                        }
                    }
                }
            )
        }

        // 3. CONFIRMATION DE BILLET
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

        // 4. PORTEFEUILLE HORS-LIGNE (CARTE CINÉPASS + BILLETS)
        composable(PatheRoute.Wallet.route) {
            WalletAndPassScreen(
                cinePass = cinePass,
                upcomingTickets = upcomingTickets,
                onBack = {
                    if (!navController.popBackStack()) {
                        val fallback = if (authManager.getClientId() != null) {
                            PatheRoute.Schedule.route
                        } else {
                            PatheRoute.Login.route
                        }
                        navController.navigate(fallback) {
                            popUpTo(PatheRoute.Wallet.route) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}
