package com.example.mobiletest.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class Location(
    val code: String,
    val displayName: String,
    val url: String
)