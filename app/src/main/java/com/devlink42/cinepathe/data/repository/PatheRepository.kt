package com.devlink42.cinepathe.data.repository

import com.devlink42.cinepathe.data.local.CachedCinePass
import com.devlink42.cinepathe.data.local.CachedTicket
import com.devlink42.cinepathe.data.local.OfflineTicketsStore
import com.devlink42.cinepathe.data.model.BookingOrderDto
import com.devlink42.cinepathe.data.remote.PatheAccountService
import com.devlink42.cinepathe.data.remote.PatheApiService
import com.devlink42.cinepathe.data.remote.PatheBookingService
import com.devlink42.cinepathe.data.remote.SeatPayload
import com.devlink42.cinepathe.ui.MovieUiModel
import com.devlink42.cinepathe.ui.ShowtimeUiModel
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PatheRepository(
    private val apiService: PatheApiService,
    private val bookingService: PatheBookingService,
    private val accountService: PatheAccountService,
    private val offlineStore: OfflineTicketsStore
) {
    val upcomingTicketsFlow: Flow<List<CachedTicket>> = offlineStore.upcomingTicketsFlow
    val cinePassFlow: Flow<CachedCinePass?> = offlineStore.cinePassFlow

    suspend fun syncAccountAndPass() {
        runCatching {
            val activePass = accountService.fetchAndCacheCinePass()
            if (activePass != null) {
                offlineStore.saveCinePass(
                    CachedCinePass(
                        cardNumber = activePass.cardNumber,
                        holderName = activePass.label ?: "Abonné CinéPass",
                        expirationDate = activePass.expirationDate
                    )
                )
            }
        }.onFailure { android.util.Log.e("PatheSync", "Erreur CinéPass", it) }

        runCatching {
            val remoteTickets = accountService.getUpcomingTickets()
            remoteTickets.forEach { dto ->
                val startMillis = parseIsoToMillis(dto.startTime)
                offlineStore.saveTicket(
                    CachedTicket(
                        bookingCode = dto.bookingId,
                        qrContent = dto.qrCodeContent,
                        movieTitle = dto.movieTitle,
                        cinemaName = dto.cinemaName,
                        auditorium = dto.auditoriumName,
                        seatLabel = dto.seats.joinToString(", "),
                        startTimeMillis = startMillis,
                        endTimeMillis = startMillis + (2 * 3600_000L)
                    )
                )
            }
        }.onFailure { android.util.Log.e("PatheSync", "Erreur Billets", it) }
    }

    suspend fun getMoviesSchedule(
        cinemaSlug: String,
        date: String,
        movieSlugs: List<String> = emptyList()
    ): List<MovieUiModel> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val slugsToLoad = movieSlugs.ifEmpty {
            apiService.getCinemaShows(cinemaSlug).shows
                .filter { (_, entry) -> entry.days.containsKey(date) }
                .keys
                .toList()
        }

        return slugsToLoad.mapNotNull { slug ->
            runCatching {
                val details = apiService.getShowDetails(slug)
                val showtimesDto = apiService.getShowtimes(slug, cinemaSlug, date)

                val mappedShowtimes = showtimesDto
                    .filter { it.status.equals("available", ignoreCase = true) }
                    .mapIndexed { idx, st ->
                        val start = runCatching { LocalDateTime.parse(st.time, formatter) }.getOrNull()
                        val end = runCatching { LocalDateTime.parse(st.endTime, formatter) }.getOrNull()
                        val formatTag = st.tags.firstOrNull {
                            it.equals("IMAX", true) || it.equals("4DX", true) || it.equals("DOLBY", true)
                        }?.uppercase()

                        ShowtimeUiModel(
                            id = st.refCmd.substringAfterLast("/").ifBlank { "$slug-$idx" },
                            refCmd = st.refCmd,
                            timeFormatted = start?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: st.time.takeLast(8).take(5),
                            endFormatted = end?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: st.endTime.takeLast(8).take(5),
                            version = st.version.uppercase(),
                            formatTag = formatTag,
                            auditorium = "Salle ${st.auditoriumName}"
                        )
                    }

                if (mappedShowtimes.isEmpty()) return@mapNotNull null

                MovieUiModel(
                    slug = details.slug,
                    title = details.title,
                    durationMinutes = details.duration,
                    posterUrl = details.posterPath?.md ?: details.posterPath?.sm.orEmpty(),
                    showtimes = mappedShowtimes
                )
            }.getOrNull()
        }
    }

    suspend fun loadBookingOrder(cinemaId: Int, orderId: String): BookingOrderDto {
        return bookingService.getBookingOrder(cinemaId, orderId)
    }

    suspend fun allocateSeats(
        cinemaId: Int,
        showingId: Int,
        orderId: String,
        seats: List<SeatPayload>
    ): Boolean {
        return bookingService.allocateSeats(cinemaId, showingId, orderId, seats)
    }

    suspend fun cancelOrder(cinemaId: Int, orderId: String) {
        bookingService.cancelOrder(cinemaId, orderId)
    }

    suspend fun saveConfirmedTicketLocally(ticket: CachedTicket) {
        offlineStore.saveTicket(ticket)
    }

    private fun parseIsoToMillis(iso: String): Long = runCatching {
        LocalDateTime.parse(iso, DateTimeFormatter.ISO_DATE_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrElse { System.currentTimeMillis() }
}
