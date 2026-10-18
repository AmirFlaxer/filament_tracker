package com.amir.filamenttracker.data.di

import android.content.Context
import androidx.room.Room
import com.amir.filamenttracker.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(AppDatabase.SEED_CALLBACK)
            .build()

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()
}
