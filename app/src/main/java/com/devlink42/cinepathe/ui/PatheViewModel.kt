package com.devlink42.cinepathe.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devlink42.cinepathe.data.remote.SeatPayload
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class PatheUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // Écran 1 : Séances
    val dates: List<LocalDate> = List(7) { LocalDate.now().plusDays(it.toLong()) },
    val selectedDateIndex: Int = 0,
    val movies: List<MovieUiModel> = emptyList(),
    // Écran 2 : Sélection de siège
    val activeMovieTitle: String = "",
    val activeAuditorium: String = "",
    val activeCinemaId: Int = 3337, // Pathé Aubière
    val activeShowingId: Int = 0,
    val activeOrderId: String? = null,
    val seats: List<SeatNode> = emptyList(),
    val hasCinePass: Boolean = true,
    // Écran 3 : Billet finalisé
    val confirmedTicket: ConfirmedTicketUiModel? = null
)

data class ConfirmedTicketUiModel(
    val movieTitle: String,
    val cinemaName: String = "Pathé Aubière",
    val cinemaAddress: String = "Avenue Lavoisier, 63170 Aubière",
    val auditorium: String,
    val seatLabel: String,
    val bookingCode: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val qrBitmap: ImageBitmap,
    val googleWalletUrl: String?
)

class PatheViewModel(
    private val apiService: PatheApiService,
    private val bookingService: PatheBookingService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatheUiState())
    val uiState: StateFlow<PatheUiState> = _uiState.asStateFlow()

    // Indique si une place a été verrouillée côté serveur mais pas encore validée en billet
    private var hasUnconfirmedLockedSeats = false

    init {
        loadScheduleForDate(0)
    }

    fun loadScheduleForDate(index: Int) {
        _uiState.update { it.copy(selectedDateIndex = index, isLoading = true, errorMessage = null) }
        val targetDate = _uiState.value.dates[index].format(DateTimeFormatter.ISO_LOCAL_DATE)

        viewModelScope.launch {
            runCatching {
                // Appel à ton repository qui agrège les films + showtimes du Pathé Aubière
                // apiService.getCinemaSchedule("cinema-pathe-aubiere", targetDate)
                emptyList<MovieUiModel>() // À brancher sur ton repository
            }.onSuccess { loadedMovies ->
                _uiState.update { it.copy(movies = loadedMovies, isLoading = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    /**
     * Étape 1 : Ouvre la séance et récupère l'orderId + le plan de salle
     */
    fun openShowtime(movie: MovieUiModel, showtime: ShowtimeUiModel, onReady: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                // Appelle GET s.pathe.fr/api/fr-FR/order/{cinemaId}/{orderId}/booking
                // et mappe les sièges en List<SeatNode>
                val generatedOrderId = "ORDER_ID_FROM_API"
                val parsedSeats = emptyList<SeatNode>()
                generatedOrderId to parsedSeats
            }.onSuccess { (orderId, seatNodes) ->
                hasUnconfirmedLockedSeats = false
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activeMovieTitle = movie.title,
                        activeAuditorium = showtime.auditorium,
                        activeShowingId = showtime.id.toIntOrNull() ?: 0,
                        activeOrderId = orderId,
                        seats = seatNodes
                    )
                }
                onReady()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = "Impossible de charger la salle : ${err.message}") }
            }
        }
    }

    /**
     * Étape 2 : Verrouille les sièges (POST seat/allocation) puis finalise le billet CinéPass (0€)
     */
    fun confirmSeatsAndBook(selectedSeats: List<SeatNode>, onTicketConfirmed: () -> Unit) {
        val state = _uiState.value
        val orderId = state.activeOrderId ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    activeMovieTitle = movie.title,
                    activeAuditorium = bookingOrder.screenNames[showtime.id] ?: showtime.auditorium,
                    activeShowingId = showtime.id.toIntOrNull() ?: 0,
                    activeOrderId = bookingOrder.orderId,
                    seats = layout.seats,
                    // Vérifie automatiquement si le CinéPass est détecté sur la commande
                    hasCinePass = bookingOrder.selection.prepaidCodes.any { card ->
                        card.subCategory.equals("CINEPASS", ignoreCase = true)
                    }
                )
            }
            runCatching {
                val payloads = selectedSeats.map {
                    SeatPayload(
                        rowIndex = it.rowIndex,
                        rowName = it.rowName,
                        seatIndex = it.colIndex,
                        seatName = it.seatName,
                        seatType = if (it.isPmr) "WCH" else "STD"
                    )
                }

                // 1. Verrouillage serveur (démarre le chrono Vista)
                val allocated = bookingService.allocateSeats(
                    cinemaId = state.activeCinemaId,
                    showingId = state.activeShowingId,
                    orderId = orderId,
                    seats = payloads
                )
                if (!allocated) error("Cette place vient d'être prise par un autre spectateur.")
                hasUnconfirmedLockedSeats = true

                // 2. Finalisation de la commande (CinéPass 0,00 €) -> récupère bookingCode + JWT Wallet
                val bookingCode = "WQTQZPL" // Issu de la réponse de validation finale
                val walletJwtUrl = "https://google.com..." // Issu de l'API Wallet

                ConfirmedTicketUiModel(
                    movieTitle = state.activeMovieTitle,
                    auditorium = state.activeAuditorium,
                    seatLabel = selectedSeats.joinToString(", ") { it.id },
                    bookingCode = bookingCode,
                    startTimeMillis = System.currentTimeMillis() + 3600_000L, // À mapper depuis showtime.time
                    endTimeMillis = System.currentTimeMillis() + 10800_000L,
                    qrBitmap = generateTicketQrCode(bookingCode),
                    googleWalletUrl = walletJwtUrl
                )
            }.onSuccess { ticket ->
                // Le billet est validé : on ne doit surtout plus annuler la commande !
                hasUnconfirmedLockedSeats = false
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activeOrderId = null,
                        confirmedTicket = ticket
                    )
                }
                onTicketConfirmed()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    /**
     * Libère immédiatement les sièges si tu quittes l'écran avant la validation finale
     */
    fun releasePendingOrderIfAny() {
        val orderId = _uiState.value.activeOrderId ?: return
        val cinemaId = _uiState.value.activeCinemaId

        // On nettoie l'état local immédiatement
        _uiState.update { it.copy(activeOrderId = null, seats = emptyList()) }

        if (hasUnconfirmedLockedSeats) {
            hasUnconfirmedLockedSeats = false
            viewModelScope.launch(NonCancellable) {
                runCatching {
                    // Appelle DELETE https://pathe.fr{cinemaId}/{orderId}
                    bookingService.cancelOrder(cinemaId, orderId)
                }
            }
        }
    }

    override fun onCleared() {
        releasePendingOrderIfAny()
        super.onCleared()
    }
}
