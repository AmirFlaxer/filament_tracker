package com.amir.filamenttracker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.amir.filamenttracker.domain.materialKind

/**
 * Version 1 to 2: materials get a main material (family) and a sub-type (variant), and stock
 * targets are keyed by family + color instead of material + color.
 *
 * Nothing is dropped except the old stock_targets table, whose rows are copied first. Material
 * names are never touched: only the new family and variant columns are filled in.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE materials ADD COLUMN family TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE materials ADD COLUMN variant TEXT NOT NULL DEFAULT ''")

        val names = db.query("SELECT id, name FROM materials").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
        }
        for ((id, name) in names) {
            val kind = materialKind(name)
            db.execSQL(
                "UPDATE materials SET family = ?, variant = ? WHERE id = ?",
                arrayOf<Any>(kind.family, kind.variant ?: "", id),
            )
        }

        // This must match the stock_targets table in schemas/.../2.json exactly.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stock_targets_new` (`family` TEXT NOT NULL, `colorId` INTEGER NOT NULL, " +
                "`minSealed` INTEGER NOT NULL, PRIMARY KEY(`family`, `colorId`), " +
                "FOREIGN KEY(`colorId`) REFERENCES `colors`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        // Targets of materials in one family and color merge into one, keeping the highest minimum
        // and the family spelling of the lowest material id.
        db.execSQL(
            """
            INSERT INTO stock_targets_new (family, colorId, minSealed)
            SELECT (SELECT m2.family FROM materials m2 WHERE m2.family = UPPER(m.family) COLLATE NOCASE
                    ORDER BY m2.id LIMIT 1),
                   t.colorId, MAX(t.minSealed)
            FROM stock_targets t
            JOIN materials m ON m.id = t.materialId
            GROUP BY UPPER(m.family), t.colorId
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE stock_targets")
        db.execSQL("ALTER TABLE stock_targets_new RENAME TO stock_targets")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_targets_colorId` ON `stock_targets` (`colorId`)")
    }
}
