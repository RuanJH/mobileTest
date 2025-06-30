package com.example.mobiletest.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.mobiletest.model.Booking
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject

/**
 * 本地持久化处理
 *
 * Created by RuanJunHao on 2025/6/29.
 */
class BookingDataStore @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("booking_store") }
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveBooking(booking: Booking) {
        dataStore.edit { prefs ->
            prefs[BOOKING_JSON_KEY] = json.encodeToString(Booking.serializer(), booking)
            prefs[CACHE_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }

    suspend fun getBooking(): CachedBooking? {
        val prefs = dataStore.data.catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }.first()

        val bookingJson = prefs[BOOKING_JSON_KEY] ?: return null
        val booking = json.decodeFromString(Booking.serializer(), bookingJson)
        val timestamp = prefs[CACHE_TIMESTAMP_KEY] ?: 0L
        return CachedBooking(booking, timestamp)
    }

    companion object {
        val BOOKING_JSON_KEY = stringPreferencesKey("booking_json")
        val CACHE_TIMESTAMP_KEY = longPreferencesKey("cache_timestamp")
    }
}

data class CachedBooking(
    val booking: Booking,
    val timestamp: Long
)