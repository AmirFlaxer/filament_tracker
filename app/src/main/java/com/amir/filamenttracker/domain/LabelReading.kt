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
        .replace(CODE_WITH_DIGIT, " ")
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

    val brand = findKnownBrand(text, knownBrands) ?: firstLineBrand(text, knownFamilies, colorKeys)
    return LabelReading(brand, family, parts.variant, parts.finish, colors)
}

private fun findKnownBrand(text: String, knownBrands: List<String>): String? =
    knownBrands.filter { it.isNotBlank() }.sortedByDescending { it.length }.firstOrNull { brand ->
        Regex("""(?<![\p{L}\p{N}])""" + Regex.escape(brand.trim()) + """(?![\p{L}\p{N}])""",RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

private fun firstLineBrand(text: String, knownFamilies: List<String>, colorKeys: Set<String>): String? {
    val line = text.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
    val isCandidate = LATIN_LINE.matches(line) &&
        knownFamilies.none { it.equals(line, ignoreCase = true) } &&
        !isKnownTermsOnly(line) &&
        colorKey(line) !in colorKeys
    return line.takeIf { isCandidate }
}
