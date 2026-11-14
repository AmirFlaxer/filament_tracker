package com.amir.filamenttracker.presentation.scan

import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.LabelReading
import com.amir.filamenttracker.domain.colorKey
import com.amir.filamenttracker.domain.colorParts
import com.amir.filamenttracker.domain.isGenericBrand

sealed interface ScanMatch {
    /** brandConfirmed=false: matched by line and color only; the label's brand is [labelBrand]. */
    data class Found(val spools: List<SpoolRow>, val brandConfirmed: Boolean, val labelBrand: String?) : ScanMatch
    data class GenericSuggestion(val labelBrand: String, val spools: List<SpoolRow>) : ScanMatch
    data class KnownProductNoSpool(val productId: Long) : ScanMatch
    data object None : ScanMatch
}

/** Decides which registered spools a label reading (and an optional scanned barcode's product) points to. */
fun matchScan(reading: LabelReading, barcodeProductId: Long?, candidates: List<SpoolRow>): ScanMatch {
    if (barcodeProductId != null) {
        val byProduct = candidates.filter { it.productId == barcodeProductId }
        if (byProduct.isNotEmpty()) return ScanMatch.Found(byProduct, true, reading.brand)
        val byText = matchByText(reading, candidates)
        return if (byText == ScanMatch.None) ScanMatch.KnownProductNoSpool(barcodeProductId) else byText
    }
    return matchByText(reading, candidates)
}

private fun matchByText(reading: LabelReading, candidates: List<SpoolRow>): ScanMatch {
    val family = reading.family
    if (family == null || reading.colors.isEmpty()) return ScanMatch.None
    // A generic word on the label names no maker, so it counts as an unknown brand.
    val brand = reading.brand?.takeUnless { isGenericBrand(it) }

    val line = candidates.filter {
        it.materialFamily.equals(family, ignoreCase = true) &&
            it.materialFinish.equals(reading.finish, ignoreCase = true) &&
            colorParts(it.colorName).map { part -> colorKey(part) }.toSet() == reading.colors
    }

    if (brand != null) {
        val sameBrand = line.filter { it.brand.equals(brand, ignoreCase = true) }
        if (sameBrand.isNotEmpty()) return ScanMatch.Found(variantFirst(sameBrand, reading), true, brand)
        val generics = line.filter { isGenericBrand(it.brand) }
        if (generics.isNotEmpty()) return ScanMatch.GenericSuggestion(brand, generics)
    }
    if (line.isNotEmpty()) return ScanMatch.Found(variantFirst(line, reading), brand == null, brand)
    return ScanMatch.None
}

/** Stable sort: spools of the label's variant first, the rest keep their order. */
private fun variantFirst(spools: List<SpoolRow>, reading: LabelReading): List<SpoolRow> =
    spools.sortedBy { if (it.materialVariant.equals(reading.variant, ignoreCase = true)) 0 else 1 }
