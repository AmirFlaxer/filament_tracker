package com.amir.filamenttracker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.amir.filamenttracker.domain.MaterialParts
import com.amir.filamenttracker.domain.composeMaterialName
import com.amir.filamenttracker.domain.materialKind
import com.amir.filamenttracker.domain.normalizeMaterialParts

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

/** Version 2 to 3: orders can be archived. Every existing order starts outside the archive. */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orders ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Version 3 to 4: materials get a finish, and stock targets are keyed by family + finish + color.
 *
 * Every sub-type is split into a sub-type and a finish in canonical words ("SILK" is the finish
 * "Silk", "Rapid" and "hi speed" are the sub-type "High Speed"), and the name is recomposed from
 * them. Materials that end up the same (ignoring case) merge into the lowest id: its products are
 * moved over, and it stays active if any of them was. Existing targets keep the plain finish.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE materials ADD COLUMN finish TEXT NOT NULL DEFAULT ''")

        data class Row(val id: Long, val family: String, val parts: MaterialParts, val archived: Boolean)
        val rows = db.query("SELECT id, family, variant, archived FROM materials ORDER BY id").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Row(c.getLong(0), c.getString(1), normalizeMaterialParts(c.getString(2), ""), c.getInt(3) != 0))
                }
            }
        }
        val groups = rows.groupBy { Triple(it.family.uppercase(), it.parts.variant.uppercase(), it.parts.finish.uppercase()) }.values

        for (group in groups) {
            val keep = group.first()
            for (merged in group.drop(1)) {
                db.execSQL("UPDATE products SET materialId = ? WHERE materialId = ?", arrayOf<Any>(keep.id, merged.id))
                db.execSQL("DELETE FROM materials WHERE id = ?", arrayOf<Any>(merged.id))
            }
        }
        // Names are unique: clear them all first, so a new name never meets an old one still in place.
        db.execSQL("UPDATE materials SET name = '#' || id")
        val taken = mutableSetOf<String>()
        for (group in groups) {
            val keep = group.first()
            val composed = composeMaterialName(keep.family, keep.parts.variant, keep.parts.finish)
            val name = if (taken.add(composed)) composed else "$composed #${keep.id}"
            db.execSQL(
                "UPDATE materials SET name = ?, variant = ?, finish = ?, archived = ? WHERE id = ?",
                arrayOf<Any>(name, keep.parts.variant, keep.parts.finish, if (group.all { it.archived }) 1 else 0, keep.id),
            )
        }

        // This must match the stock_targets table in schemas/.../4.json exactly.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stock_targets_new` (`family` TEXT NOT NULL, `colorId` INTEGER NOT NULL, " +
                "`minSealed` INTEGER NOT NULL, `finish` TEXT NOT NULL, PRIMARY KEY(`family`, `finish`, `colorId`), " +
                "FOREIGN KEY(`colorId`) REFERENCES `colors`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("INSERT INTO stock_targets_new (family, colorId, minSealed, finish) SELECT family, colorId, minSealed, '' FROM stock_targets")
        db.execSQL("DROP TABLE stock_targets")
        db.execSQL("ALTER TABLE stock_targets_new RENAME TO stock_targets")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_targets_colorId` ON `stock_targets` (`colorId`)")
    }
}
