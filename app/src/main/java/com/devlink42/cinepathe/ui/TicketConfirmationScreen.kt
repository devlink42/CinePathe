package com.devlink42.cinepathe.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TicketConfirmationScreen(
    movieTitle: String,
    cinemaName: String,
    cinemaAddress: String,
    auditorium: String,
    seatLabel: String,
    bookingCode: String,       // ex: "WQTQZPL"
    startTimeMillis: Long,
    endTimeMillis: Long,
    qrBitmap: ImageBitmap,
    googleWalletUrl: String?   // ex: "https://pay.google.com/gp/v/save/eyJ0eXAi..."
) {
    val context = LocalContext.current

    // Boost automatique de la luminosité pour le scan en salle
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f
        window?.attributes = window?.attributes?.apply { screenBrightness = 1f }
        onDispose {
            window?.attributes = window?.attributes?.apply { screenBrightness = originalBrightness }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSurface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(movieTitle, fontWeight = FontWeight.ExtraBold, color = Color.Black, style = MaterialTheme.typography.titleLarge)
                Text("$cinemaName • $auditorium • Place $seatLabel", color = Color.DarkGray)

                Spacer(modifier = Modifier.height(20.dp))

                Image(
                    bitmap = qrBitmap,
                    contentDescription = "QR Code Billet",
                    modifier = Modifier.size(220.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Réservation : $bookingCode", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bouton natif Ajouter au Calendrier
        OutlinedButton(
            onClick = {
                openCalendarInsert(
                    context = context,
                    title = "Cinéma : $movieTitle",
                    location = "$cinemaName, $cinemaAddress",
                    description = "Salle : $auditorium | Place : $seatLabel | Dossier : $bookingCode",
                    startMillis = startTimeMillis,
                    endMillis = endTimeMillis
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PatheGold)
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Ajouter à mon calendrier")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bouton Ajouter au Google Wallet (via l'URL JWT signée par Pathé)
        if (!googleWalletUrl.isNullOrBlank()) {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(googleWalletUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                Icon(Icons.Default.Wallet, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter à Google Wallet", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun openCalendarInsert(
    context: Context,
    title: String,
    location: String,
    description: String,
    startMillis: Long,
    endMillis: Long
) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, title)
        putExtra(CalendarContract.Events.EVENT_LOCATION, location)
        putExtra(CalendarContract.Events.DESCRIPTION, description)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
    }
    context.startActivity(intent)
}
