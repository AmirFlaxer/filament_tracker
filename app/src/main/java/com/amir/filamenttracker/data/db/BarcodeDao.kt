package com.amir.filamenttracker.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface BarcodeDao {
    @Query("SELECT productId FROM product_barcodes WHERE code = :code")
    suspend fun productFor(code: String): Long?

    @Upsert
    suspend fun save(entity: ProductBarcodeEntity)
}
