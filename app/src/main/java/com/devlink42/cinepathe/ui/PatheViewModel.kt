package com.devlink42.cinepathe.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devlink42.cinepathe.data.local.CachedCinePass
import com.devlink42.cinepathe.data.local.CachedTicket
import com.devlink42.cinepathe.data.local.generateTicketQrCode
import com.devlink42.cinepathe.data.model.CinemaInfo
import com.devlink42.cinepathe.data.remote.SeatPayload
import com.devlink42.cinepathe.data.remote.toRoomLayout
import com.devlink42.cinepathe.data.repository.PatheRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class PatheUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedCinema: CinemaInfo? = null,
    val availableCinemas: List<CinemaInfo> = emptyList(),
    val showCinemaPicker: Boolean = false,
    val dates: List<LocalDate> = List(7) { LocalDate.now().plusDays(it.toLong()) },
    val selectedDateIndex: Int = 0,
    val movies: List<MovieUiModel> = emptyList(),
    val activeMovieTitle: String = "",
    val activeAuditorium: String = "",
    val activeCinemaId: Int = 0,
    val activeShowingId: Int = 0,
    val activeOrderId: String? = null,
    val seats: List<SeatNode> = emptyList(),
    val hasCinePass: Boolean = true,
    val confirmedTicket: ConfirmedTicketUiModel? = null
)

data class ConfirmedTicketUiModel(
    val movieTitle: String,
    val cinemaName: String,
    val cinemaAddress: String,
    val auditorium: String,
    val seatLabel: String,
    val bookingCode: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val qrBitmap: ImageBitmap,
    val googleWalletUrl: String?
)

class PatheViewModel(
    private val repository: PatheRepository
) : ViewModel() {

    private val initialCinema = repository.getSavedCinema()

    private val _uiState = MutableStateFlow(
        PatheUiState(
            selectedCinema = initialCinema,
            activeCinemaId = initialCinema?.cinemaId ?: 0,
            showCinemaPicker = initialCinema == null
        )
    )
    val uiState: StateFlow<PatheUiState> = _uiState.asStateFlow()

    val upcomingTickets: StateFlow<List<CachedTicket>> = repository.upcomingTicketsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cinePass: StateFlow<CachedCinePass?> = repository.cinePassFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var hasUnconfirmedLockedSeats = false

    init {
        syncUserAccount()
        loadCinemasList()
        if (initialCinema != null) {
            loadScheduleForDate(0)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun syncUserAccount() {
        viewModelScope.launch {
            repository.syncAccountAndPass()
        }
    }

    fun loadCinemasList() {
        viewModelScope.launch {
            runCatching { repository.getAllCinemas() }
                .onSuccess { list ->
                    _uiState.update { it.copy(availableCinemas = list) }
                }
        }
    }

    fun openCinemaPicker() {
        if (_uiState.value.availableCinemas.isEmpty()) {
            loadCinemasList()
        }
        _uiState.update { it.copy(showCinemaPicker = true) }
    }

    fun closeCinemaPicker() {
        if (_uiState.value.selectedCinema != null) {
            _uiState.update { it.copy(showCinemaPicker = false) }
        }
    }

    fun selectCinema(cinema: CinemaInfo) {
        repository.saveSelectedCinema(cinema)
        _uiState.update {
            it.copy(
                selectedCinema = cinema,
                activeCinemaId = cinema.cinemaId,
                showCinemaPicker = false,
                movies = emptyList()
            )
        }
        loadScheduleForDate(_uiState.value.selectedDateIndex)
    }

    fun loadScheduleForDate(index: Int) {
        val cinema = _uiState.value.selectedCinema ?: return
        _uiState.update { it.copy(selectedDateIndex = index, isLoading = true, errorMessage = null) }
        val targetDate = _uiState.value.dates[index].format(DateTimeFormatter.ISO_LOCAL_DATE)

        viewModelScope.launch {
            runCatching {
                repository.getMoviesSchedule(
                    cinemaSlug = cinema.slug,
                    date = targetDate
                )
            }.onSuccess { loadedMovies ->
                android.util.Log.d("PatheSchedule", "Films chargés : ${loadedMovies.size}")
                _uiState.update { it.copy(movies = loadedMovies, isLoading = false) }
            }.onFailure { err ->
                android.util.Log.e("PatheSchedule", "Erreur chargement films", err)
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun openShowtime(movie: MovieUiModel, showtime: ShowtimeUiModel, onReady: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val (resolvedCinemaId, resolvedShowingId, bookingOrder) =
                    repository.startBookingFromShowtime(_uiState.value.activeCinemaId, showtime.refCmd)
                val layout = bookingOrder.toRoomLayout(resolvedShowingId)
                Triple(resolvedCinemaId, resolvedShowingId, Pair(bookingOrder, layout))
            }.onSuccess { (resolvedCinemaId, resolvedShowingId, pair) ->
                val (bookingOrder, layout) = pair
                hasUnconfirmedLockedSeats = false
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activeCinemaId = resolvedCinemaId,
                        activeMovieTitle = movie.title,
                        activeAuditorium = bookingOrder.screenNames[resolvedShowingId.toString()] ?: showtime.auditorium,
                        activeShowingId = resolvedShowingId,
                        activeOrderId = bookingOrder.orderId,
                        seats = layout.seats,
                        hasCinePass = bookingOrder.selection.prepaidCodes.any { card ->
                            card.subCategory.equals("CINEPASS", ignoreCase = true)
                        } || cinePass.value != null
                    )
                }
                onReady()
            }.onFailure { err ->
                android.util.Log.e("PatheBooking", "Erreur ouverture séance", err)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Impossible d'ouvrir la séance : ${err.message}"
                    )
                }
            }
        }
    }

    fun confirmSeatsAndBook(selectedSeats: List<SeatNode>, onTicketConfirmed: () -> Unit) {
        val state = _uiState.value
        val orderId = state.activeOrderId ?: return
        val cinema = state.selectedCinema ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

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

                val allocated = repository.allocateSeats(
                    cinemaId = state.activeCinemaId,
                    showingId = state.activeShowingId,
                    orderId = orderId,
                    seats = payloads
                )
                if (!allocated) error("Cette place vient d'être prise par un autre spectateur.")
                hasUnconfirmedLockedSeats = true

                val bookingCode = orderId.take(7).uppercase()
                val seatLabel = selectedSeats.joinToString(", ") { it.id }
                val startMillis = System.currentTimeMillis() + 3600_000L
                val endMillis = startMillis + 7200_000L

                val ticket = ConfirmedTicketUiModel(
                    movieTitle = state.activeMovieTitle,
                    cinemaName = cinema.name,
                    cinemaAddress = cinema.address,
                    auditorium = state.activeAuditorium,
                    seatLabel = seatLabel,
                    bookingCode = bookingCode,
                    startTimeMillis = startMillis,
                    endTimeMillis = endMillis,
                    qrBitmap = generateTicketQrCode(bookingCode),
                    googleWalletUrl = null
                )

                repository.saveConfirmedTicketLocally(
                    CachedTicket(
                        bookingCode = bookingCode,
                        qrContent = bookingCode,
                        movieTitle = ticket.movieTitle,
                        cinemaName = ticket.cinemaName,
                        cinemaAddress = ticket.cinemaAddress,
                        auditorium = ticket.auditorium,
                        seatLabel = seatLabel,
                        startTimeMillis = startMillis,
                        endTimeMillis = endMillis
                    )
                )

                ticket
            }.onSuccess { ticket ->
                hasUnconfirmedLockedSeats = false
                repository.closeBookingSession()
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

    fun releasePendingOrderIfAny() {
        val orderId = _uiState.value.activeOrderId
        val cinemaId = _uiState.value.activeCinemaId

        _uiState.update { it.copy(activeOrderId = null, seats = emptyList()) }

        viewModelScope.launch(NonCancellable) {
            if (orderId != null && hasUnconfirmedLockedSeats) {
                hasUnconfirmedLockedSeats = false
                runCatching { repository.cancelOrder(cinemaId, orderId) }
            }
            repository.closeBookingSession()
        }
    }

    override fun onCleared() {
        releasePendingOrderIfAny()
        super.onCleared()
    }

    class Factory(private val repository: PatheRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PatheViewModel(repository) as T
        }
    }
}
