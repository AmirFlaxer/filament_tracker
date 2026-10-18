package com.amir.filamenttracker.presentation.inventory

import com.amir.filamenttracker.data.AlertRow
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.AlertKind
import com.amir.filamenttracker.domain.model.SpoolStatus

data class ListRow(
    val materialId: Long,
    val colorId: Long,
    val label: String,
    val colorHex: String?,
    val counts: Map<SpoolStatus, Int>,
    val slots: List<Int> = emptyList(),
)

data class ListSection(val title: String, val colorHex: String?, val rows: List<ListRow>)

data class StatusTableRow(
    val materialId: Long,
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

data class CellMaterial(val materialId: Long, val materialName: String)

data class MaterialCell(val counts: Map<SpoolStatus, Int>, val missing: Boolean, val materials: List<CellMaterial>) {
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

/** A row per material and color, with a variant such as "Matte" shown beside the color name. */
private fun materialColorRows(spools: List<SpoolRow>): List<ListRow> =
    spools.groupBy { it.materialId to it.colorId }.values.map { group ->
        val first = group.first()
        val variant = first.materialVariant
        ListRow(
            materialId = first.materialId,
            colorId = first.colorId,
            label = if (variant.isBlank()) first.colorName else "${first.colorName} · $variant",
            colorHex = first.colorHex,
            counts = group.countsByStatus(),
        )
    }.sortedBy { it.label }

fun byMaterial(spools: List<SpoolRow>): List<ListSection> {
    val rows = live(spools)
    val names = familyNames(rows)
    return rows.groupBy { familyKey(it.materialFamily) }.toSortedMap().map { (key, group) ->
        ListSection(names.getValue(key), null, materialColorRows(group))
    }
}

fun byColor(spools: List<SpoolRow>): List<ListSection> =
    live(spools).groupBy { it.colorId }.values.map { group ->
        val first = group.first()
        val rows = group.groupBy { it.materialId }.values.map { perMaterial ->
            val m = perMaterial.first()
            ListRow(m.materialId, m.colorId, m.materialName, m.colorHex, perMaterial.countsByStatus())
        }.sortedBy { it.label }
        ListSection(first.colorName, first.colorHex, rows)
    }.sortedBy { it.title }

private val statusSections = listOf(
    SpoolStatus.IN_USE to "בשימוש",
    SpoolStatus.OPEN_STORED to "פתוחים באחסון",
    SpoolStatus.SEALED to "סגורים",
    SpoolStatus.ORDERED to "בדרך",
)

fun byStatus(spools: List<SpoolRow>): List<ListSection> {
    val rows = live(spools)
    return statusSections.mapNotNull { (status, title) ->
        val inStatus = rows.filter { it.status == status }
        if (inStatus.isEmpty()) return@mapNotNull null
        val listRows = inStatus.groupBy { it.materialId to it.colorId }.values.map { group ->
            val first = group.first()
            ListRow(
                materialId = first.materialId,
                colorId = first.colorId,
                label = "${first.materialName} ${first.colorName}",
                colorHex = first.colorHex,
                counts = mapOf(status to group.size),
                slots = if (status == SpoolStatus.IN_USE) group.mapNotNull { it.amsSlot }.sorted() else emptyList(),
            )
        }.sortedBy { it.label }
        ListSection(title, null, listRows)
    }
}

fun statusTable(spools: List<SpoolRow>): List<StatusTableSection> {
    val rows = live(spools)
    val names = familyNames(rows)
    return rows.groupBy { familyKey(it.materialFamily) }.toSortedMap().map { (key, group) ->
        val tableRows = materialColorRows(group).map { row ->
            StatusTableRow(
                materialId = row.materialId,
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
                val materials = if (inCell.isNotEmpty()) {
                    inCell.map { CellMaterial(it.materialId, it.materialName) }
                } else {
                    emptyList()
                }
                MaterialCell(
                    counts = inCell.countsByStatus(),
                    missing = inCell.isEmpty() && missingHere.isNotEmpty(),
                    materials = materials.distinct().sortedBy { it.materialName },
                )
            }
        }
        MaterialTableRow(colorId, colorName, colorHex, cells)
    }
    return MaterialTable(families.map { names.getValue(it) }, tableRows)
}
