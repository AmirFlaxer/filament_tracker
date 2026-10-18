package com.amir.filamenttracker.testutil

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.amir.filamenttracker.data.db.AppDatabase

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .addCallback(AppDatabase.SEED_CALLBACK)
        .allowMainThreadQueries()
        .build()
