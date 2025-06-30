package com.example.mobiletest.navigation

sealed class Routes(val route: String) {
    object Booking: Routes("booking")
}