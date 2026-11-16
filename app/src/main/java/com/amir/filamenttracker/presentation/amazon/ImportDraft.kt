package com.amir.filamenttracker.presentation.amazon

import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.domain.colorKey
import com.amir.filamenttracker.domain.amazon.AmazonOrder
import com.amir.filamenttracker.domain.amazon.isClosed
import com.amir.filamenttracker.domain.amazon.isFilamentTitle
import com.amir.filamenttracker.domain.amazon.readAmazonTitle
import com.amir.filamenttracker.domain.composeColorName
import com.amir.filamenttracker.domain.importColorName
import com.amir.filamenttracker.domain.sameColor
import com.amir.filamenttracker.domain.normalizeMaterialParts

/** One color of a card; [productId] null means a new product, created on save. */
data class ImportRow(val colorName: String, val quantity: Int, val productId: Long?)

data class ImportCard(
    val key: Int,
    val sourceTitle: String,
    val itemQuantity: Int,
    val brand: String,
    val family: String,
    val variant: String,
    val finish: String,
    val weightGrams: Int,
    val rows: List<ImportRow>,
    /** More than zero while the colors are unknown and still have to be marked. */
    val colorsToMark: Int,
)

data class ImportChoices(val orders: List<AmazonOrder>, val skippedTitles: List<String>)

data class CatalogLists(val brands: List<String>, val families: List<String>, val colors: List<String>)

/**
 * Orders worth importing: not closed (delivered, cancelled, returned) and not yet registered. Each keeps only its filament items;
 * the titles of the other items of shown orders are returned as skipped.
 */
fun importableOrders(orders: List<AmazonOrder>, existingNumbers: Set<String>, knownFamilies: List<String>): ImportChoices {
    val existing = existingNumbers.map { it.trim() }.toSet()
    val shown = mutableListOf<AmazonOrder>()
    val skipped = mutableListOf<String>()
    for (order in orders) {
        if (order.isClosed || order.orderNumber.trim() in existing) continue
        val (filament, other) = order.items.partition { isFilamentTitle(it.title, knownFamilies) }
        skipped += other.map { it.title }
        if (filament.isNotEmpty()) shown += order.copy(items = filament)
    }
    return ImportChoices(shown, skipped)
}

fun buildCards(order: AmazonOrder, products: List<ProductRow>, lists: CatalogLists): List<ImportCard> =
    order.items.filter { isFilamentTitle(it.title, lists.families) }.mapIndexed { index, item ->
        val r = readAmazonTitle(item.title, lists.brands, lists.families, lists.colors)
        val quantity = item.quantity
        // Colors take the catalog's name when they exist there, otherwise their Hebrew name.
        val colors = r.colors.map { importColorName(it, lists.colors) }
        val rows = when {
            r.colors.isEmpty() -> emptyList()
            r.combined -> listOf(ImportRow(composeColorName(colors), quantity * r.spoolsPerUnit, null))
            // One spool per color, only when the colors account for the whole pack; otherwise the
            // found colors stay as they are and the owner marks the rest.
            else -> colors.map { ImportRow(it, quantity, null) }
        }
        val toMark = when {
            r.colors.isEmpty() -> r.spoolsPerUnit
            r.combined -> 0
            else -> (r.spoolsPerUnit - r.colors.size).coerceAtLeast(0)
        }
        ImportCard(
            key = index, sourceTitle = item.title, itemQuantity = quantity,
            brand = r.brand, family = r.family.orEmpty(), variant = r.variant, finish = r.finish,
            weightGrams = r.weightGrams, rows = rows,
            colorsToMark = toMark,
        ).rematch(products)
    }

/** The catalog product that is exactly this card's specification in [colorName], if any. */
fun matchProduct(products: List<ProductRow>, card: ImportCard, colorName: String): Long? {
    val parts = normalizeMaterialParts(card.variant, card.finish)
    return products.firstOrNull { p ->
        val pParts = normalizeMaterialParts(p.materialVariant, p.materialFinish)
        p.brand.trim().equals(card.brand.trim(), ignoreCase = true) &&
            p.materialFamily.equals(card.family, ignoreCase = true) &&
            pParts == parts &&
            p.weightGrams == card.weightGrams &&
            sameColor(colorName, p.colorName)
    }?.productId
}

/** Matches every row again, after the card's specification or a color changed. */
fun ImportCard.rematch(products: List<ProductRow>): ImportCard =
    copy(rows = rows.map { it.copy(productId = matchProduct(products, this, it.colorName)) })

/** Spools the card stands for, counting colors not yet marked at one item's quantity each. */
val List<ImportCard>.spoolCount: Int
    get() = sumOf { card -> card.rows.sumOf { it.quantity } + card.colorsToMark * card.itemQuantity }

/** Sets one row's quantity; zero or less removes the row. */
fun ImportCard.withRowQuantity(index: Int, quantity: Int, products: List<ProductRow>): ImportCard {
    if (index !in rows.indices) return this
    val next = if (quantity <= 0) rows.filterIndexed { i, _ -> i != index }
    else rows.mapIndexed { i, r -> if (i == index) r.copy(quantity = quantity) else r }
    return copy(rows = next).rematch(products)
}

/** Renames one row's color; a blank name is ignored. */
fun ImportCard.withRowColor(index: Int, colorName: String, products: List<ProductRow>): ImportCard {
    val name = colorName.trim()
    if (index !in rows.indices || name.isEmpty()) return this
    return copy(rows = rows.mapIndexed { i, r -> if (i == index) r.copy(colorName = name) else r }).rematch(products)
}

/**
 * Marks colors on a card whose colors were not in the title: each name adds a row of one item's
 * quantity (or tops up the row of the same color) and uses up one of [ImportCard.colorsToMark].
 */
fun ImportCard.markColors(names: List<String>, products: List<ProductRow>): ImportCard {
    var next = rows
    var marked = 0
    for (raw in names) {
        val name = raw.trim()
        if (name.isEmpty()) continue
        marked++
        val at = next.indexOfFirst { colorKey(it.colorName) == colorKey(name) }
        next = if (at >= 0) next.mapIndexed { i, r -> if (i == at) r.copy(quantity = r.quantity + itemQuantity) else r }
        else next + ImportRow(name, itemQuantity, null)
    }
    return copy(rows = next, colorsToMark = (colorsToMark - marked).coerceAtLeast(0)).rematch(products)
}

/** Applies an edited specification (brand, material, weight); the colors stay as they are. */
fun ImportCard.withSpec(
    brand: String, family: String, variant: String, finish: String, weightGrams: Int, products: List<ProductRow>,
): ImportCard = copy(
    brand = brand.trim(), family = family.trim(), variant = variant.trim(), finish = finish.trim(), weightGrams = weightGrams,
).rematch(products)
