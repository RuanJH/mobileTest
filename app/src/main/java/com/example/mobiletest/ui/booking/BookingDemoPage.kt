package com.example.mobiletest.ui.booking

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * BookingDemo 页面
 *
 * Created by RuanJunHao on 2025/6/29.
 */
@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDemoPage(viewModel: BookingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 显示Loading
    LaunchedEffect(Unit) {
        viewModel.loadBooking()
    }

    // 利用生命周期 STARTED 实现 每次页面出现（不一定是首次创建）时都调用接口
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            println("Lifecycle.State.STARTED")
            viewModel.refreshBooking()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BookingDemo") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            when (uiState) {
                is BookingUiState.Loading -> Text("Loading...")
                is BookingUiState.Success -> Text(
                    text = (uiState as BookingUiState.Success).booking.shipReference,
                    color = Color.Green
                )
                is BookingUiState.Error -> Text(
                    text = (uiState as BookingUiState.Error).message,
                    color = Color.Red
                )
            }
        }
    }
}
