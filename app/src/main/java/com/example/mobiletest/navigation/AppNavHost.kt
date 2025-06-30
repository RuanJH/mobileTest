package com.example.mobiletest.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.mobiletest.ui.booking.BookingDemoPage
import com.example.mobiletest.ui.booking.BookingViewModel

@Composable
fun AppNavHost(navHostController: NavHostController) {
    NavHost(navController = navHostController, startDestination = Routes.Booking.route) {

        composable(route = Routes.Booking.route) {
            val bookingViewModel = hiltViewModel<BookingViewModel>()
            BookingDemoPage(viewModel = bookingViewModel)
        }
    }
}