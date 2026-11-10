package com.amir.filamenttracker.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.data.db.MIGRATION_1_2
import com.amir.filamenttracker.data.db.MIGRATION_2_3
import com.amir.filamenttracker.data.db.MIGRATION_3_4
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
    fun `migration 2 to 3 keeps every order and starts them all outside the archive`() {
        val v2 = helper.createDatabase("migration-2-3.db", 2)
        v2.execSQL("INSERT INTO materials (id, name, family, variant, archived) VALUES (1,'PLA','PLA','',0)")
        v2.execSQL("INSERT INTO colors (id, name, hex, archived) VALUES (10,'שחור',NULL,0)")
        v2.execSQL("INSERT INTO products (id, brand, materialId, colorId, weightGrams, note, archived) VALUES (1,'A',1,10,1000,'',0)")
        v2.execSQL("INSERT INTO orders (id, vendor, orderedAt, expectedAt, orderNumber) VALUES (1,'Amazon',1000,NULL,'111'),(2,'Ali',2000,NULL,NULL)")
        v2.execSQL("INSERT INTO spools (id, productId, orderId, status, amsSlot) VALUES (1,1,1,'SEALED',NULL),(2,1,2,'ORDERED',NULL)")
        v2.close()

        val db = helper.runMigrationsAndValidate("migration-2-3.db", 3, true, MIGRATION_2_3)

        val orders = db.query("SELECT id, orderNumber, archived FROM orders ORDER BY id").use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1), c.getInt(2))) }
        }
        assertEquals(listOf(Triple(1L, "111", 0), Triple(2L, null, 0)), orders)
        assertEquals(2, db.count("spools"))
    }

    @Test
    fun `migration 3 to 4 splits finishes out, merges aliases and keeps every product and spool`() {
        val v3 = helper.createDatabase("migration-3-4.db", 3)
        // The materials on the phone at 0.4.1, plus an archived twin.
        v3.execSQL(
            "INSERT INTO materials (id, name, family, variant, archived) VALUES " +
                "(1,'PLA','PLA','',0),(2,'PETG','PETG','',0),(3,'PETG hi speed','PETG','hi speed',0)," +
                "(4,'PLA +','PLA','+',0),(5,'PETG translucent','PETG','translucent',0),(6,'PLA SILK','PLA','SILK',0)," +
                "(7,'Rapid PETG','PETG','Rapid',0),(8,'PLA Silk','PLA','silk',1)",
        )
        v3.execSQL("INSERT INTO colors (id, name, hex, archived) VALUES (10,'אפור',NULL,0),(11,'זהב',NULL,0)")
        v3.execSQL(
            "INSERT INTO products (id, brand, materialId, colorId, weightGrams, note, archived) VALUES " +
                "(1,'A',3,10,1000,'',0),(2,'B',7,10,1000,'',0),(3,'A',6,11,1000,'',0),(4,'C',8,11,1000,'',0),(5,'A',1,10,1000,'',0)",
        )
        v3.execSQL("INSERT INTO spools (id, productId, orderId, status, amsSlot) VALUES (1,1,NULL,'IN_USE',1),(2,2,NULL,'SEALED',NULL),(3,4,NULL,'SEALED',NULL)")
        v3.execSQL("INSERT INTO stock_targets (family, colorId, minSealed) VALUES ('PETG',10,2)")
        v3.close()

        val db = helper.runMigrationsAndValidate("migration-3-4.db", 4, true, MIGRATION_3_4)

        val materials = db.query("SELECT id, name, variant, finish, archived FROM materials ORDER BY id").use { c ->
            buildList { while (c.moveToNext()) add(listOf(c.getLong(0).toString(), c.getString(1), c.getString(2), c.getString(3), c.getInt(4).toString())) }
        }
        assertEquals(
            listOf(
                listOf("1", "PLA", "", "", "0"),
                listOf("2", "PETG", "", "", "0"),
                listOf("3", "PETG High Speed", "High Speed", "", "0"),
                listOf("4", "PLA+", "+", "", "0"),
                listOf("5", "PETG Translucent", "", "Translucent", "0"),
                listOf("6", "PLA Silk", "", "Silk", "0"),
            ),
            materials,
        )
        val productMaterials = db.query("SELECT id, materialId FROM products ORDER BY id").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getLong(1)) }
        }
        assertEquals(listOf(1L to 3L, 2L to 3L, 3L to 6L, 4L to 6L, 5L to 1L), productMaterials)
        assertEquals(3, db.count("spools"))
        val targets = db.query("SELECT family, finish, colorId, minSealed FROM stock_targets").use { c ->
            buildList { while (c.moveToNext()) add(listOf(c.getString(0), c.getString(1), c.getLong(2).toString(), c.getInt(3).toString())) }
        }
        assertEquals(listOf(listOf("PETG", "", "10", "2")), targets)
    }

    @Test
    fun `migration 3 to 4 of an empty database validates`() {
        helper.createDatabase("migration-3-4-empty.db", 3).close()
        helper.runMigrationsAndValidate("migration-3-4-empty.db", 4, true, MIGRATION_3_4).close()
    }

    @Test
    fun `families that differ only by case merge using the lowest id spelling and the highest minimum`() {
        val v1 = helper.createDatabase("migration-case.db", 1)
        v1.execSQL("INSERT INTO materials (id, name, archived) VALUES (1,'petg HF',0),(2,'PETG',0)")
        v1.execSQL("INSERT INTO colors (id, name, hex, archived) VALUES (10,'שחור',NULL,0)")
        v1.execSQL("INSERT INTO stock_targets (materialId, colorId, minSealed) VALUES (1,10,1),(2,10,2)")
        v1.close()
        val db = helper.runMigrationsAndValidate("migration-case.db", 2, true, MIGRATION_1_2)
        val targets = db.query("SELECT family, colorId, minSealed FROM stock_targets").use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getLong(1), c.getInt(2))) }
        }
        assertEquals(listOf(Triple("petg", 10L, 2)), targets)
    }

    @Test
    fun `migration of an empty database validates`() {
        helper.createDatabase("migration-empty.db", 1).close()
        helper.runMigrationsAndValidate("migration-empty.db", 2, true, MIGRATION_1_2).close()
    }
}
