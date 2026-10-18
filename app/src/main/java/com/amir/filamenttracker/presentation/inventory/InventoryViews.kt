package com.amir.filamenttracker.presentation.inventory

import com.amir.filamenttracker.data.AlertRow
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.AlertKind
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
)

/** [isColor] marks a by-color section, whose title is a color name drawn as a chip even when it has no stored hue. */
data class ListSection(val title: String, val colorHex: String?, val rows: List<ListRow>, val isColor: Boolean = false)

data class StatusTableRow(
    val family: String,
    val colorId: Long,
    val label: String,
    val colorHex: String?,
    val inUse: Int,
    val open: Int,
    val sealed: Int,
    val ordered: Int,
) {
    val total: Int get() = inUse + open + sealed + ordered
}

data class StatusTableSection(val family: String, val rows: List<StatusTableRow>)

data class MaterialCell(val counts: Map<SpoolStatus, Int>, val missing: Boolean) {
    val total: Int get() = counts.values.sum()
}

data class MaterialTableRow(val colorId: Long, val colorName: String, val colorHex: String?, val cells: List<MaterialCell?>)

data class MaterialTable(val families: List<String>, val rows: List<MaterialTableRow>)

/** Finished spools are history, not stock. Ordered ones stay, shown as "on the way". */
private fun live(spools: List<SpoolRow>) = spools.filter { it.status != SpoolStatus.FINISHED }

private fun familyKey(family: String) = family.uppercase()

/** Family spelling per [familyKey]: taken from the alphabetically first material name, so it is stable. */
private fun familyNames(spools: List<SpoolRow>, extraFamilies: Collection<String> = emptyList()): Map<String, String> {
    val fromSpools = spools.sortedBy { it.materialName }.reversed().associate { familyKey(it.materialFamily) to it.materialFamily }
    return extraFamilies.sorted().reversed().associate { familyKey(it) to it }.filterKeys { it !in fromSpools } + fromSpools
}

private fun List<SpoolRow>.countsByStatus(): Map<SpoolStatus, Int> = groupingBy { it.status }.eachCount()

/** A row per main material and color: every variant of the family counts together, the variant shows on the spools screen. */
private fun familyColorRows(spools: List<SpoolRow>, names: Map<String, String>): List<ListRow> =
    spools.groupBy { familyKey(it.materialFamily) to it.colorId }.map { (key, group) ->
        val first = group.first()
        ListRow(
            family = names.getValue(key.first),
            colorId = first.colorId,
            label = first.colorName,
            colorHex = first.colorHex,
            counts = group.countsByStatus(),
            colorName = first.colorName,
        )
    }.sortedBy { it.label }

fun byMaterial(spools: List<SpoolRow>): List<ListSection> {
    val rows = live(spools)
    val names = familyNames(rows)
    return rows.groupBy { familyKey(it.materialFamily) }.toSortedMap().map { (key, group) ->
        ListSection(names.getValue(key), null, familyColorRows(group, names))
    }
}

fun byColor(spools: List<SpoolRow>): List<ListSection> {
    val rows = live(spools)
    val names = familyNames(rows)
    return rows.groupBy { it.colorId }.values.map { group ->
        val first = group.first()
        val perFamily = group.groupBy { familyKey(it.materialFamily) }.map { (key, inFamily) ->
            val family = names.getValue(key)
            ListRow(family, first.colorId, family, first.colorHex, inFamily.countsByStatus())
        }.sortedBy { it.label }
        ListSection(first.colorName, first.colorHex, perFamily, isColor = true)
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
    val names = familyNames(rows)
    return statusSections.mapNotNull { (status, title) ->
        val inStatus = rows.filter { it.status == status }
        if (inStatus.isEmpty()) return@mapNotNull null
        val listRows = inStatus.groupBy { familyKey(it.materialFamily) to it.colorId }.map { (key, group) ->
            val first = group.first()
            val family = names.getValue(key.first)
            ListRow(
                family = family,
                colorId = first.colorId,
                label = "$family ${first.colorName}",
                colorHex = first.colorHex,
                counts = mapOf(status to group.size),
                slots = if (status == SpoolStatus.IN_USE) group.mapNotNull { it.amsSlot }.sorted() else emptyList(),
                colorName = first.colorName,
            )
        }.sortedBy { it.label }
        ListSection(title, null, listRows)
    }
}

fun statusTable(spools: List<SpoolRow>): List<StatusTableSection> {
    val rows = live(spools)
    val names = familyNames(rows)
    return rows.groupBy { familyKey(it.materialFamily) }.toSortedMap().map { (key, group) ->
        val tableRows = familyColorRows(group, names).map { row ->
            StatusTableRow(
                family = row.family,
                colorId = row.colorId,
                label = row.label,
                colorHex = row.colorHex,
                inUse = row.counts[SpoolStatus.IN_USE] ?: 0,
                open = row.counts[SpoolStatus.OPEN_STORED] ?: 0,
                sealed = row.counts[SpoolStatus.SEALED] ?: 0,
                ordered = row.counts[SpoolStatus.ORDERED] ?: 0,
            )
        }
        StatusTableSection(names.getValue(key), tableRows)
    }
}

fun materialTable(spools: List<SpoolRow>, alerts: List<AlertRow>): MaterialTable {
    val rows = live(spools)
    val missing = alerts.filter { it.kind == AlertKind.MISSING }
    val names = familyNames(rows, missing.map { it.family })
    val families = names.keys.sorted()

    // One entry per color, taking name and hue from whichever side mentions it.
    val colors = (rows.map { Triple(it.colorId, it.colorName, it.colorHex) } +
        missing.map { Triple(it.colorId, it.colorName, it.colorHex) })
        .distinctBy { it.first }.sortedBy { it.second }

    val tableRows = colors.map { (colorId, colorName, colorHex) ->
        val cells = families.map { family ->
            val inCell = rows.filter { it.colorId == colorId && familyKey(it.materialFamily) == family }
            val missingHere = missing.filter { it.colorId == colorId && familyKey(it.family) == family }
            if (inCell.isEmpty() && missingHere.isEmpty()) {
                null
            } else {
                MaterialCell(
                    counts = inCell.countsByStatus(),
                    missing = inCell.isEmpty() && missingHere.isNotEmpty(),
                )
            }
        }
        MaterialTableRow(colorId, colorName, colorHex, cells)
    }
    return MaterialTable(families.map { names.getValue(it) }, tableRows)
}
