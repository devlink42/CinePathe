package com.devlink42.cinepathe.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.devlink42.cinepathe.data.local.CachedCinePass
import com.devlink42.cinepathe.data.local.CachedTicket
import com.devlink42.cinepathe.data.local.generateBarcodeBitmap
import com.devlink42.cinepathe.data.local.generateQrBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletAndPassScreen(
    cinePass: CachedCinePass?,
    upcomingTickets: List<CachedTicket>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val totalPages = 1 + upcomingTickets.size
    val pagerState = rememberPagerState(pageCount = { totalPages })

    // Luminosité maximale automatique tant que cet écran est affiché
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val previousBrightness = window?.attributes?.screenBrightness ?: -1f
        window?.attributes = window?.attributes?.apply { screenBrightness = 1f }
        onDispose {
            window?.attributes = window?.attributes?.apply { screenBrightness = previousBrightness }
        }
    }

    Scaffold(
        containerColor = DarkSurface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (pagerState.currentPage == 0) "Ma Carte CinéPass" else "Billet (${pagerState.currentPage}/${upcomingTickets.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 24.dp),
                pageSpacing = 16.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                if (page == 0) {
                    CinePassFullCard(cinePass = cinePass)
                } else {
                    val ticket = upcomingTickets[page - 1]
                    OfflineTicketFullCard(ticket = ticket)
                }
            }

            // Indicateur de pages (Carte + Billets)
            if (totalPages > 1) {
                Row(
                    modifier = Modifier.padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(totalPages) { index ->
                        val active = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (active) 24.dp else 8.dp)
                                .background(
                                    color = if (active) PatheGold else Color.Gray,
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CinePassFullCard(cinePass: CachedCinePass?) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF26262C), Color(0xFF121214))
                    )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PATHÉ", color = PatheGold, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Surface(color = PatheGold, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "CINÉPASS",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (cinePass != null) {
                val qrBmp = remember(cinePass.cardNumber) { generateQrBitmap(cinePass.cardNumber) }
                val barBmp =
                    remember(cinePass.cardNumber) { generateBarcodeBitmap(cinePass.cardNumber) }

                // Bloc blanc haute lisibilité pour les scanners optiques
                Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = qrBmp,
                            contentDescription = "QR CinéPass",
                            modifier = Modifier.size(180.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Image(
                            bitmap = barBmp,
                            contentDescription = "Code-barres CinéPass",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = cinePass.cardNumber,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(cinePass.holderName, color = Color.White, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Aucune carte CinéPass enregistrée", color = Color.Gray)
            }
        }
    }
}

@Composable
private fun OfflineTicketFullCard(ticket: CachedTicket) {
    val dateFormatter = remember { SimpleDateFormat("EEE d MMM • HH:mm", Locale.FRENCH) }
    val qrBmp = remember(ticket.qrContent) { generateQrBitmap(ticket.qrContent) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                ticket.movieTitle,
                color = Color.Black,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dateFormatter.format(Date(ticket.startTimeMillis)),
                color = Color.DarkGray,
                fontWeight = FontWeight.SemiBold
            )
            Text("${ticket.cinemaName} • ${ticket.auditorium}", color = Color.Gray)

            Spacer(modifier = Modifier.height(20.dp))

            Image(
                bitmap = qrBmp,
                contentDescription = "QR Code Séance",
                modifier = Modifier.size(210.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLACE", color = Color.Gray, fontSize = 11.sp)
                    Text(
                        ticket.seatLabel,
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DOSSIER", color = Color.Gray, fontSize = 11.sp)
                    Text(
                        ticket.bookingCode,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}
