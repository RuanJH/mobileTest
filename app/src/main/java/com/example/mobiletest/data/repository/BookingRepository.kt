package com.example.mobiletest.data.repository

import com.example.mobiletest.data.remote.BookingService
import com.example.mobiletest.data.store.BookingDataStore
import com.example.mobiletest.model.Booking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BookingRepository
 *
 * Created by RuanJunHao on 2025/6/29.
 */
class BookingRepository @Inject constructor(
    private val service: BookingService,
    private val dataStore: BookingDataStore
) {
    private val cacheDurationMillis = 5 * 60 * 1000L // 20 seconds
    private val refreshInterval = 30 * 1000L // 10 seconds

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 使用 StateFlow 作为唯一的状态来源
    private val _bookingState = MutableStateFlow<Booking?>(null)
    val bookingState: StateFlow<Booking?> = _bookingState.asStateFlow()

    init {
        scope.launch {
            startAutoRefreshLoop()
        }
    }

    // 不再需要返回值，状态通过 StateFlow 更新
    suspend fun getBooking() {
        val cachedData = dataStore.getBooking()
        val shouldRefresh = cachedData == null || isExpired(cachedData.timestamp)

        if (shouldRefresh) {
            println("refresh data")
            refreshBooking()
        } else {
            // 确保 StateFlow 的值和缓存一致
            println("get cache data")
            _bookingState.value = cachedData.booking

        }
    }

    suspend fun refreshBooking() {
        try {
            val newBooking = service.fetchBookingData()
            dataStore.saveBooking(newBooking)
            _bookingState.value = newBooking // 更新 StateFlow
            println("Booking data refreshed: $newBooking")
        } catch (e: Exception) {
            println("Error fetching booking data: ${e.message}")
            val cached = dataStore.getBooking()?.booking
            _bookingState.value = cached
            throw e // 抛出异常让ViewModel处理
        }
    }

    private suspend fun startAutoRefreshLoop() {
        while (true) {
            val cachedData = dataStore.getBooking()
            if (cachedData == null || isExpired(cachedData.timestamp)) {
                refreshBooking()
            }
            delay(refreshInterval)
        }
    }

    private fun isExpired(savedTime: Long): Boolean {
        return (System.currentTimeMillis() - savedTime) > cacheDurationMillis
    }

    fun cancel() {
        scope.cancel()
    }
}
