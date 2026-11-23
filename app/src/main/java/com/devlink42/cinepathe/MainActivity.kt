package com.devlink42.cinepathe

import com.devlink42.cinepathe.ui.PatheRoute

val startRoute = if (authManager.getClientId() != null) {
    PatheRoute.Schedule.route
} else {
    "login"
}
val openCinePassDirectly = intent?.action == "com.devlink42.cinepathe.ACTION_OPEN_CINEPASS"

val startRoute = when {
    openCinePassDirectly -> "wallet" // Ouvre immédiatement WalletAndPassScreen (hors-ligne)
    authManager.getClientId() != null -> PatheRoute.Schedule.route
    else -> "login"
}
