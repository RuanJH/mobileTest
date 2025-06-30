package com.example.mobiletest.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class OriginAndDestinationPair(
    val origin: Location,
    val destination: Location,
    val originCity: String,
    val destinationCity: String
)