package com.example.mobiletest.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class Segment(
    val id: Int,
    val originAndDestinationPair: OriginAndDestinationPair
)
