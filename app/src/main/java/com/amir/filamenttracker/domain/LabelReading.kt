package com.amir.filamenttracker.domain

/** What a spool label says: the maker, the main material, the sub-type, the finish and the colors (as [colorKey] keys). */
data class LabelReading(
    val brand: String?,
    val family: String?,
    val variant: String,
    val finish: String,
    val colors: Set<String>,
)

/** A blank brand, or one spelled "generic" or "גנרי", names no real maker. */
fun isGenericBrand(brand: String): Boolean = brand.trim().lowercase().let { it.isEmpty() || it == "גנרי" || it == "generic" }

private val CLEAR_AFTER_COLOR = Regex("""color\s*:\s*clear""", RegexOption.IGNORE_CASE)
private val CODE_WITH_DIGIT = Regex("""[\p{L}-]*\d[\p{L}\p{N}-]*""")
private val CARBON_WORD = Regex("""carbon\s+(\p{L}+)""", RegexOption.IGNORE_CASE)
private val NOT_TERM_CHAR = Regex("""[^\p{L}\p{N}+\s-]""")
private val BRAND_STOPLIST = setOf("FILAMENT", "PREMIUM", "PLASTIC", "SPOOL", "NEW")
private val LATIN_LINE = Regex("^[A-Za-z]{2,20}$")

private fun wordsOf(text: String): List<String> =
    text.split(Regex("""[^\p{L}\p{N}+]+""")).filter { it.isNotEmpty() }

/** Reads the text of a label. Known brands, families and colors come from the spools already registered. */
fun readLabel(text: String, knownBrands: List<String>, knownFamilies: List<String>, knownColors: List<String>): LabelReading {
    val words = wordsOf(text)

    var family: String? = null
    var plus = false
    for (word in words) {
        val match = knownFamilies.firstOrNull { it.equals(word, ignoreCase = true) }
        val stem = word.trimEnd('+')
        val stemMatch = if (word.endsWith("+")) knownFamilies.firstOrNull { it.equals(stem, ignoreCase = true) } else null
        if (match != null || stemMatch != null) {
            family = (match ?: stemMatch)!!.uppercase()
            plus = match == null
            break
        }
    }

    val knownParts = knownColors.flatMap { colorParts(it) }
    val colorKeys = paletteColorKeys + knownParts.map { colorKey(it) }

    // A code with a digit ("HS-1216") is a batch number, never a term. "Carbon" before a color word is a color name.
    val termText = text.replace(CLEAR_AFTER_COLOR, "color:")
        .replace(NOT_TERM_CHAR, " ")
        .replace(CODE_WITH_DIGIT) { m -> if (isMaterialCode(m.value, knownFamilies)) m.value else " " }
        .replace(CARBON_WORD) { m -> if (colorKey(m.groupValues[1]) in colorKeys) m.groupValues[1] else m.value }
    val variantSeed = (listOfNotNull("+".takeIf { plus }) + VARIANT_TERMS.filter { hasTerm(termText, it) }).joinToString(" ")
    val finishSeed = (COMMON_FINISH_TERMS + MORE_FINISH_TERMS).filter { hasTerm(termText, it) }.joinToString(" ")
    val parts = normalizeMaterialParts(variantSeed, finishSeed)

    // Known colors of several words match as whole phrases first, longest first, and their words are consumed.
    var rest = text
    val colors = mutableSetOf<String>()
    for (phrase in knownParts.filter { it.trim().contains(Regex("""\s""")) }.sortedByDescending { it.length }) {
        val pattern = Regex("""(?<![\p{L}\p{N}])""" + phrase.trim().split(Regex("""\s+""")).joinToString("""\s+""") { Regex.escape(it) } + """(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
        if (pattern.containsMatchIn(rest)) {
            colors += colorKey(phrase)
            rest = rest.replace(pattern, " ")
        }
    }
    colors += wordsOf(rest).map { colorKey(it) }.filter { it in colorKeys }

    val known = findKnownBrand(text, knownBrands)
    // Two different known brands on one label ("Compatible with Bambu Lab AMS") name no single maker.
    val brand = if (known.ambiguous) null else known.brand ?: firstLineBrand(text, knownFamilies, colorKeys)
    return LabelReading(brand, family, parts.variant, parts.finish, colors)
}

private class KnownBrand(val brand: String?, val ambiguous: Boolean)

/** The longest known brand; overlapping matches ("Bambu" in "Bambu Lab") keep the longest, two separate brands are ambiguous. */
private fun findKnownBrand(text: String, knownBrands: List<String>): KnownBrand {
    val found = mutableListOf<Pair<String, IntRange>>()
    for (brand in knownBrands.filter { it.isNotBlank() }.map { it.trim() }.distinctBy { it.lowercase() }.sortedByDescending { it.length }) {
        val pattern = Regex("""(?<![\p{L}\p{N}])""" + Regex.escape(brand) + """(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
        pattern.findAll(text).forEach { m ->
            if (found.none { (_, r) -> m.range.first <= r.last && r.first <= m.range.last }) found += brand to m.range
        }
    }
    val distinct = found.map { it.first.lowercase() }.distinct()
    return KnownBrand(found.firstOrNull()?.first, distinct.size >= 2)
}

private fun firstLineBrand(text: String, knownFamilies: List<String>, colorKeys: Set<String>): String? {
    val line = text.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
    val isCandidate = LATIN_LINE.matches(line) &&
        knownFamilies.none { it.equals(line, ignoreCase = true) } &&
        line.uppercase() !in BRAND_STOPLIST &&
        !isKnownTermsOnly(line) &&
        colorKey(line) !in colorKeys
    return line.takeIf { isCandidate }
}

/** A code like "PA6-CF" or "PA12": its first hyphen segment is a known family, then optional digits. */
private fun isMaterialCode(token: String, knownFamilies: List<String>): Boolean {
    val head = token.substringBefore('-').trimEnd { it.isDigit() }
    return head.isNotEmpty() && knownFamilies.any { it.equals(head, ignoreCase = true) }
}
