package com.amir.filamenttracker.presentation.inventory

import com.amir.filamenttracker.data.AlertRow
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.AlertKind
import com.amir.filamenttracker.domain.lineName
import com.amir.filamenttracker.domain.model.SpoolStatus

data class ListRow(
    val family: String,
    val colorId: Long,
    val label: String,
    val colorHex: String?,
    val counts: Map<SpoolStatus, Int>,
    val slots: List<Int> = emptyList(),
    /** Set when the row stands for a color, so the screen can draw it as a chip; null when the label is plain text. */
    val colorName: String? = null,
    /** The finish of the row's stock line; empty is the plain finish. */
    val finish: String = "",
)

/** [isColor] marks a by-color section, whose title is a color name drawn as a chip even when it has no stored hue. */
data class ListSection(val title: String, val colorHex: String?, val rows: List<ListRow>, val isColor: Boolean = false)

data class StatusTableRow(
    val family: String,
    val colorId: Long,
    val label: String,
    val colorHex: String?,
    val finish: String = "",
    val inUse: Int,
    val open: Int,
    val sealed: Int,
    val ordered: Int,
    /** Missing or running low against its target; null otherwise. */
    val alert: AlertKind? = null,
) {
    val total: Int get() = inUse + open + sealed + ordered
}

data class StatusTableSection(val family: String, val rows: List<StatusTableRow>)

/** [missing] marks an empty cell kept for its target; [alert] is missing or running low, else null. */
data class MaterialCell(val counts: Map<SpoolStatus, Int>, val missing: Boolean, val alert: AlertKind? = null) {
    val total: Int get() = counts.values.sum()
}

data class MaterialTableRow(val colorId: Long, val colorName: String, val colorHex: String?, val cells: List<MaterialCell?>)

/** A stock line: spools of one main material and finish (any sub-type) count together. */
data class StockLine(val family: String, val finish: String) {
    val name: String get() = lineName(family, finish)
}

/** Rows are colors, columns are stock lines. */
data class MaterialTable(val lines: List<StockLine>, val rows: List<MaterialTableRow>)

/** Finished spools are history, not stock. Ordered ones stay, shown as "on the way". */
private fun live(spools: List<SpoolRow>) = spools.filter { it.status != SpoolStatus.FINISHED }

private fun familyKey(family: String) = family.uppercase()

private typealias LineKey = Pair<String, String>

private fun lineKey(family: String, finish: String): LineKey = family.uppercase() to finish.uppercase()

private val SpoolRow.lineKey: LineKey get() = lineKey(materialFamily, materialFinish)

/**
 * Spelling of every stock line: the family spelling is shared by all lines of the family, and each
 * part is taken from the alphabetically first material name, so it is stable.
 */
private class Spellings(spools: List<SpoolRow>, extraLines: Collection<StockLine> = emptyList()) {
    private val families: Map<String, String>
    private val finishes: Map<LineKey, String>

    init {
        val sorted = spools.sortedBy { it.materialName }.reversed()
        val extras = extraLines.sortedBy { it.name }.reversed()
        families = extras.associate { familyKey(it.family) to it.family } +
            sorted.associate { familyKey(it.materialFamily) to it.materialFamily }
        finishes = extras.associate { lineKey(it.family, it.finish) to it.finish } +
            sorted.associate { it.lineKey to it.materialFinish }
    }

    fun family(key: String): String = families.getValue(key)
    fun line(key: LineKey): StockLine = StockLine(families.getValue(key.first), finishes.getValue(key))
}

private fun List<SpoolRow>.countsByStatus(): Map<SpoolStatus, Int> = groupingBy { it.status }.eachCount()

/**
 * A row per stock line and color within one family: every sub-type counts together (it shows on the
 * spools screen), and the finish shows next to the color.
 */
private fun lineColorRows(spools: List<SpoolRow>, names: Spellings): List<ListRow> =
    spools.groupBy { it.lineKey to it.colorId }.map { (key, group) ->
        val first = group.first()
        val line = names.line(key.first)
        ListRow(
            family = line.family,
            colorId = first.colorId,
            label = first.colorName,
            colorHex = first.colorHex,
            counts = group.countsByStatus(),
            colorName = first.colorName,
            finish = line.finish,
        )
    }.sortedWith(compareBy({ it.label }, { it.finish }))

fun byMaterial(spools: List<SpoolRow>): List<ListSection> {
    val rows = live(spools)
    val names = Spellings(rows)
    return rows.groupBy { familyKey(it.materialFamily) }.toSortedMap().map { (key, group) ->
        ListSection(names.family(key), null, lineColorRows(group, names))
    }
}

fun byColor(spools: List<SpoolRow>): List<ListSection> {
    val rows = live(spools)
    val names = Spellings(rows)
    return rows.groupBy { it.colorId }.values.map { group ->
        val first = group.first()
        val perLine = group.groupBy { it.lineKey }.map { (key, inLine) ->
            val line = names.line(key)
            ListRow(line.family, first.colorId, line.name, first.colorHex, inLine.countsByStatus(), finish = line.finish)
        }.sortedBy { it.label }
        ListSection(first.colorName, first.colorHex, perLine, isColor = true)
    }.sortedBy { it.title }
}

private val statusSections = listOf(
    SpoolStatus.IN_USE to "בשימוש",
    SpoolStatus.OPEN_STORED to "פתוחים באחסון",
    SpoolStatus.SEALED to "סגורים",
    SpoolStatus.ORDERED to "בדרך",
)

fun byStatus(spools: List<SpoolRow>): List<ListSection> {
    val rows = live(spools)
    val names = Spellings(rows)
    return statusSections.mapNotNull { (status, title) ->
        val inStatus = rows.filter { it.status == status }
        if (inStatus.isEmpty()) return@mapNotNull null
        val listRows = inStatus.groupBy { it.lineKey to it.colorId }.map { (key, group) ->
            val first = group.first()
            val line = names.line(key.first)
            ListRow(
                family = line.family,
                colorId = first.colorId,
                label = "${line.name} ${first.colorName}",
                colorHex = first.colorHex,
                counts = mapOf(status to group.size),
                slots = if (status == SpoolStatus.IN_USE) group.mapNotNull { it.amsSlot }.sorted() else emptyList(),
                colorName = first.colorName,
                finish = line.finish,
            )
        }.sortedBy { it.label }
        ListSection(title, null, listRows)
    }
}

/** The alerts the tables mark: missing and running low. "On the way" is already in the counts. */
private fun tableAlerts(alerts: List<AlertRow>) =
    alerts.filter { it.kind == AlertKind.MISSING || it.kind == AlertKind.RUNNING_LOW }

private val AlertRow.lineKey: LineKey get() = lineKey(family, finish)

/**
 * Sections per family, rows per stock line and color. A target with no spools at all still gets a
 * row, so what is missing shows in the table too.
 */
fun statusTable(spools: List<SpoolRow>, alerts: List<AlertRow> = emptyList()): List<StatusTableSection> {
    val rows = live(spools)
    val marked = tableAlerts(alerts)
    val names = Spellings(rows, marked.map { StockLine(it.family, it.finish) })
    val alertOf = marked.associateBy { it.lineKey to it.colorId }
    val families = (rows.map { familyKey(it.materialFamily) } + marked.map { familyKey(it.family) }).distinct().sorted()
    return families.map { key ->
        val fromSpools = lineColorRows(rows.filter { familyKey(it.materialFamily) == key }, names).map { row ->
            StatusTableRow(
                family = row.family,
                colorId = row.colorId,
                label = row.label,
                colorHex = row.colorHex,
                finish = row.finish,
                inUse = row.counts[SpoolStatus.IN_USE] ?: 0,
                open = row.counts[SpoolStatus.OPEN_STORED] ?: 0,
                sealed = row.counts[SpoolStatus.SEALED] ?: 0,
                ordered = row.counts[SpoolStatus.ORDERED] ?: 0,
                alert = alertOf[lineKey(row.family, row.finish) to row.colorId]?.kind,
            )
        }
        val present = fromSpools.map { lineKey(it.family, it.finish) to it.colorId }.toSet()
        val missingRows = marked
            .filter { familyKey(it.family) == key && it.kind == AlertKind.MISSING && (it.lineKey to it.colorId) !in present }
            .map {
                val line = names.line(it.lineKey)
                StatusTableRow(line.family, it.colorId, it.colorName, it.colorHex, line.finish, 0, 0, 0, 0, AlertKind.MISSING)
            }
        StatusTableSection(names.family(key), (fromSpools + missingRows).sortedWith(compareBy({ it.label }, { it.finish })))
    }
}

fun materialTable(spools: List<SpoolRow>, alerts: List<AlertRow>): MaterialTable {
    val rows = live(spools)
    val missing = alerts.filter { it.kind == AlertKind.MISSING }
    val marked = tableAlerts(alerts)
    val names = Spellings(rows, missing.map { StockLine(it.family, it.finish) })
    val lineKeys = (rows.map { it.lineKey } + missing.map { lineKey(it.family, it.finish) })
        .distinct().sortedWith(compareBy({ it.first }, { it.second }))

    // One entry per color, taking name and hue from whichever side mentions it.
    val colors = (rows.map { Triple(it.colorId, it.colorName, it.colorHex) } +
        missing.map { Triple(it.colorId, it.colorName, it.colorHex) })
        .distinctBy { it.first }.sortedBy { it.second }

    val tableRows = colors.map { (colorId, colorName, colorHex) ->
        val cells = lineKeys.map { line ->
            val inCell = rows.filter { it.colorId == colorId && it.lineKey == line }
            val missingHere = missing.filter { it.colorId == colorId && lineKey(it.family, it.finish) == line }
            val alert = marked.firstOrNull { it.colorId == colorId && it.lineKey == line }?.kind
            if (inCell.isEmpty() && missingHere.isEmpty()) {
                null
            } else {
                MaterialCell(
                    counts = inCell.countsByStatus(),
                    missing = inCell.isEmpty() && missingHere.isNotEmpty(),
                    alert = alert,
                )
            }
        }
        MaterialTableRow(colorId, colorName, colorHex, cells)
    }
    return MaterialTable(lineKeys.map { names.line(it) }, tableRows)
}
