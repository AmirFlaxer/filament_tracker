package com.amir.filamenttracker.domain.amazon

import com.amir.filamenttracker.domain.MULTI_COLOR_TERMS
import com.amir.filamenttracker.domain.colorKey
import com.amir.filamenttracker.domain.hasTerm
import com.amir.filamenttracker.domain.isKnownTermsOnly
import com.amir.filamenttracker.domain.readLabel
import kotlin.math.roundToInt

/** What an Amazon product title says about the spool(s) it sells. */
data class AmazonTitleReading(
    val brand: String,
    val family: String?,
    val variant: String,
    val finish: String,
    val weightGrams: Int,
    val spoolsPerUnit: Int,
    val colors: List<String>,
    /** One spool of a combined color (dual or tri color) rather than one spool per listed color. */
    val combined: Boolean,
)

// Parts joined by + or / with no space around it; each part is one or two capitalized words.
private val COLOR_LIST = Regex("""(?<![\p{L}\p{N}])\p{Lu}\p{L}*(?: \p{Lu}\p{L}*)?(?:[+/]\p{Lu}\p{L}*(?: \p{Lu}\p{L}*)?)+""")
private val PACK_COUNT = Regex("""(\d+)\s*-?\s*(?:pack|spools?|colors?)\b""", RegexOption.IGNORE_CASE)
private val KG = Regex("""(\d+(?:\.\d+)?)\s*kg\b""", RegexOption.IGNORE_CASE)
private val NON_LETTERS = Regex("""[^\p{L}]+""")
private val FILLER_WORDS = setOf("color", "colour", "colors", "bundle", "filament", "pack")

/** Only the part before the first '|' is read: what follows is marketing text. */
private fun headOf(title: String): String = title.substringBefore('|')

fun isFilamentTitle(title: String, knownFamilies: List<String>): Boolean {
    val head = headOf(title)
    return head.contains("filament", ignoreCase = true) || readLabel(head, emptyList(), knownFamilies, emptyList()).family != null
}

fun readAmazonTitle(title: String, knownBrands: List<String>, knownFamilies: List<String>, knownColors: List<String>): AmazonTitleReading {
    val head = headOf(title)
    val full = readLabel(head, knownBrands, knownFamilies, knownColors)

    val list = findColorList(head, full.colors, knownFamilies)
    val singles = singleColors(head, full.colors)
    val colors = when {
        list != null && list.validated -> list.parts
        singles.isNotEmpty() -> singles
        list != null -> list.parts
        else -> emptyList()
    }
    // A color list can hold material words ("Wood Brown"): read the material from the head without it.
    val usedList = list != null && colors === list.parts
    val materialText = if (usedList) head.replace(list!!.text, " ") else head
    val label = readLabel(materialText, knownBrands, knownFamilies, knownColors)
    val brand = label.brand ?: head.trim().split(Regex("""\s+""")).firstOrNull().orEmpty()

    val combined = colors.size > 1 && MULTI_COLOR_TERMS.any { hasTerm(head, it) }
    val kgs = KG.findAll(head).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
    val weightGrams = kgs.firstOrNull { it in 0.0..<1.0 && it > 0.0 }?.let { (it * 1000).roundToInt() } ?: 1000
    val totalKg = kgs.maxOrNull() ?: 0.0
    val spoolsPerUnit = when {
        combined -> 1
        else -> PACK_COUNT.find(head)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
            ?: totalKg.takeIf { it > 1 }?.roundToInt()
            ?: colors.size.takeIf { it > 1 }
            ?: 1
    }
    return AmazonTitleReading(brand, label.family, label.variant, label.finish, weightGrams, spoolsPerUnit, colors, combined)
}

private class ColorList(val text: String, val parts: List<String>, val validated: Boolean)

/**
 * The first color list in [head]. A list is validated when every part holds a word of a known color;
 * the first validated list wins, otherwise the first list found is returned unvalidated.
 */
private fun findColorList(head: String, knownKeys: Set<String>, knownFamilies: List<String>): ColorList? {
    var firstFound: ColorList? = null
    for (match in COLOR_LIST.findAll(head)) {
        val rawParts = match.value.split('+', '/').toMutableList()
        // "Dual Color Red/Gold" matches from "Color": a leading material or filler word is not part of the color.
        val first = rawParts[0].split(' ')
        var kept = match.value
        if (first.size == 2 && isNonColorWord(first[0], knownFamilies)) {
            rawParts[0] = first[1]
            // The dropped word stays in the text the material is read from ("Matte Black/White").
            kept = kept.removePrefix(first[0] + " ")
        }
        // "Black/White PLA" matches up to "PLA": a trailing material word is not part of the color either.
        val last = rawParts.last().split(' ')
        if (last.size == 2 && isNonColorWord(last[1], knownFamilies)) {
            rawParts[rawParts.lastIndex] = last[0]
            kept = kept.removeSuffix(" " + last[1])
        }
        val parts = rawParts.filterNot { isNonColorWord(it, knownFamilies) }
        if (parts.size < 2) continue
        val validated = parts.all { part -> part.split(' ').any { colorKey(it) in knownKeys } }
        val candidate = ColorList(kept, parts, validated)
        if (validated) return candidate
        if (firstFound == null) firstFound = candidate
    }
    return firstFound
}

private fun isNonColorWord(word: String, knownFamilies: List<String>): Boolean =
    word.lowercase() in FILLER_WORDS || knownFamilies.any { it.equals(word, ignoreCase = true) } || isKnownTermsOnly(word)

/** Single words of [head] that name a known color, once per color, in their spelling in the title. */
private fun singleColors(head: String, knownKeys: Set<String>): List<String> =
    head.split(NON_LETTERS).filter { it.isNotEmpty() && !isKnownTermsOnly(it) && colorKey(it) in knownKeys }
        .distinctBy { colorKey(it) }
