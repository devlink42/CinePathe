package com.devlink42.cinepathe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devlink42.cinepathe.data.local.OfflineTicketsStore
import com.devlink42.cinepathe.data.remote.PatheAccountService
import com.devlink42.cinepathe.data.remote.PatheApiService
import com.devlink42.cinepathe.data.remote.PatheAuthManager
import com.devlink42.cinepathe.data.remote.PatheBookingService
import com.devlink42.cinepathe.data.remote.PatheNetworkClient
import com.devlink42.cinepathe.data.remote.installPatheAuth
import com.devlink42.cinepathe.data.repository.PatheRepository
import com.devlink42.cinepathe.ui.PatheAppNavigation
import com.devlink42.cinepathe.ui.PatheRoute
import com.devlink42.cinepathe.ui.PatheViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val httpClient = PatheNetworkClient.httpClient
        val authManager = PatheAuthManager(applicationContext, httpClient)
        httpClient.installPatheAuth(authManager)

        val offlineStore = OfflineTicketsStore(applicationContext)
        val repository = PatheRepository(
            apiService = PatheApiService(httpClient),
            bookingService = PatheBookingService(httpClient),
            accountService = PatheAccountService(httpClient, authManager),
            offlineStore = offlineStore,
            authManager = authManager
        )

        val openCinePassDirectly = intent?.action == "com.devlink42.cinepathe.ACTION_OPEN_CINEPASS"
        val startRoute = when {
            openCinePassDirectly -> PatheRoute.Wallet.route
            authManager.getClientId() != null -> PatheRoute.Schedule.route
            else -> PatheRoute.Login.route
        }

        setContent {
            val viewModel: PatheViewModel = viewModel(
                factory = PatheViewModel.Factory(repository)
            )
            PatheAppNavigation(
                viewModel = viewModel,
                authManager = authManager,
                startRoute = startRoute
            )
        }
    }
}
