package com.amir.filamenttracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MaterialEntity::class, ColorEntity::class, ProductEntity::class, OrderEntity::class,
        SpoolEntity::class, SpoolEventEntity::class, StockTargetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun spoolDao(): SpoolDao
    abstract fun orderDao(): OrderDao
    abstract fun stockDao(): StockDao

    companion object {
        const val NAME = "filament_tracker.db"

        /** The two materials in use today (spec section 1). More are added from the catalog screen. */
        val SEED_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT INTO materials (name, archived) VALUES ('PLA', 0), ('PETG', 0)")
            }
        }
    }
}
