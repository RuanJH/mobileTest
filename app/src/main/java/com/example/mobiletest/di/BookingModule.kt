package com.example.mobiletest.di

import android.content.Context
import com.example.mobiletest.data.remote.BookingService
import com.example.mobiletest.data.store.BookingDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * BookingModule
 *
 * Created by RuanJunHao on 2025/6/29.
 */
@Module
@InstallIn(SingletonComponent::class)
object BookingModule {

    @Provides
    @Singleton
    fun provideBookingService(): BookingService = BookingService()

    @Provides
    @Singleton
    fun provideBookingDataStore(@ApplicationContext context: Context): BookingDataStore = BookingDataStore(context)
}