package com.amir.filamenttracker.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.data.db.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun `migration 1 to 2 keeps every row, fills family and variant, merges targets by family`() {
        val v1 = helper.createDatabase("migration-test.db", 1)
        v1.execSQL("INSERT INTO materials (id, name, archived) VALUES (1,'PLA',0),(2,'PETG',0),(3,'Rapid PETG',0),(4,'PLA+',0),(5,'PETG++ HF',1)")
        v1.execSQL("INSERT INTO colors (id, name, hex, archived) VALUES (10,'שחור','#000000',0),(11,'לבן','#FFFFFF',0)")
        v1.execSQL(
            "INSERT INTO products (id, brand, materialId, colorId, weightGrams, note, archived) VALUES " +
                "(1,'A',1,10,1000,'',0),(2,'A',2,10,1000,'',0),(3,'A',3,10,1000,'x',0),(4,'A',4,11,1000,'',0)",
        )
        v1.execSQL("INSERT INTO spools (id, productId, orderId, status, amsSlot) VALUES (1,2,NULL,'IN_USE',3),(2,3,NULL,'SEALED',NULL)")
        v1.execSQL("INSERT INTO spool_events (id, spoolId, fromStatus, toStatus, fromSlot, toSlot, at) VALUES (1,1,NULL,'IN_USE',NULL,3,1000)")
        v1.execSQL("INSERT INTO stock_targets (materialId, colorId, minSealed) VALUES (2,10,1),(3,10,2),(1,11,1)")
        v1.close()

        val db = helper.runMigrationsAndValidate("migration-test.db", 2, true, MIGRATION_1_2)

        val kinds = db.query("SELECT name, family, variant, archived FROM materials ORDER BY id").use { c ->
            buildList { while (c.moveToNext()) add(listOf(c.getString(0), c.getString(1), c.getString(2), c.getInt(3).toString())) }
        }
        assertEquals(
            listOf(
                listOf("PLA", "PLA", "", "0"),
                listOf("PETG", "PETG", "", "0"),
                listOf("Rapid PETG", "PETG", "Rapid", "0"),
                listOf("PLA+", "PLA", "+", "0"),
                listOf("PETG++ HF", "PETG", "++ HF", "1"),
            ),
            kinds,
        )
        assertEquals(4, db.count("products"))
        assertEquals(2, db.count("spools"))
        assertEquals(1, db.count("spool_events"))
        assertEquals(2, db.count("colors"))

        val targets = db.query("SELECT family, colorId, minSealed FROM stock_targets").use { c ->
            buildSet { while (c.moveToNext()) add(Triple(c.getString(0), c.getLong(1), c.getInt(2))) }
        }
        assertEquals(setOf(Triple("PETG", 10L, 2), Triple("PLA", 11L, 1)), targets)
    }

    @Test
    fun `migration of an empty database validates`() {
        helper.createDatabase("migration-empty.db", 1).close()
        helper.runMigrationsAndValidate("migration-empty.db", 2, true, MIGRATION_1_2).close()
    }
}
