package com.example.mobiletest.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobiletest.data.repository.BookingRepository
import com.example.mobiletest.model.Booking
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BookingViewModel
 *
 * Created by RuanJunHao on 2025/6/29.
 */
@HiltViewModel
class BookingViewModel @Inject constructor(
    private val repository: BookingRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<BookingUiState>(BookingUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.bookingState.collect { booking ->
                booking?.let {
                    _uiState.value = BookingUiState.Success(it)
                    println("updated booking data: $it")
                } ?: run {
                    _uiState.value = BookingUiState.Error("No booking data available")
                }
            }
        }
    }

    fun loadBooking() {
        viewModelScope.launch {
            _uiState.value = BookingUiState.Loading
        }
    }

    fun refreshBooking() {
        viewModelScope.launch {
            try {
                repository.getBooking()
            } catch (e: Exception) {
                _uiState.value = BookingUiState.Error("fetch data error")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.cancel()
    }
}

sealed class BookingUiState {
    object Loading : BookingUiState()
    data class Success(val booking: Booking) : BookingUiState()
    data class Error(val message: String) : BookingUiState()
}