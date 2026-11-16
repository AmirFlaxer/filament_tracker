package com.amir.filamenttracker.domain

/** English base color words and their Hebrew names, as the owner writes colors in the catalog. */
private val baseHebrew = mapOf(
    "black" to "שחור", "white" to "לבן", "red" to "אדום", "yellow" to "צהוב", "orange" to "כתום",
    "blue" to "כחול", "purple" to "סגול", "bronze" to "ברונזה", "pink" to "ורוד", "gold" to "זהב",
    "gray" to "אפור", "grey" to "אפור", "green" to "ירוק", "brown" to "חום", "beige" to "בז'",
    "silver" to "כסף", "copper" to "נחושת", "cyan" to "תכלת", "turquoise" to "טורקיז", "teal" to "טורקיז",
)

/** Words that describe a base color in filament names; in Hebrew they follow the base color. */
private val descriptorHebrew = mapOf(
    "light" to "בהיר", "dark" to "כהה", "pastel" to "פסטל", "neon" to "ניאון", "earth" to "אדמה",
    "wood" to "עץ", "sky" to "שמיים", "forest" to "יער", "sand" to "חול", "olive" to "זית",
    "mint" to "מנטה", "army" to "צבאי",
)

/**
 * The Hebrew name of an English color name ("Earth Brown" is "חום אדמה"): the base color first, then
 * its descriptors. A name with any word outside the dictionary, or with no base color, stays as it is,
 * never half translated.
 */
fun hebrewColorName(name: String): String {
    val trimmed = name.trim()
    val words = trimmed.lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() }
    if (words.isEmpty() || words.any { it !in baseHebrew && it !in descriptorHebrew }) return trimmed
    val bases = words.mapNotNull { baseHebrew[it] }
    if (bases.isEmpty()) return trimmed
    return (bases + words.mapNotNull { descriptorHebrew[it] }).joinToString(" ")
}

/** True when two names are one color, whatever the language: "Earth Brown" and "חום אדמה". */
fun sameColor(a: String, b: String): Boolean = colorKey(hebrewColorName(a)) == colorKey(hebrewColorName(b))

/** The name an imported color gets: the catalog's own name when the color exists there, otherwise the Hebrew name. */
fun importColorName(raw: String, catalogColors: List<String>): String =
    catalogColors.firstOrNull { sameColor(it, raw) } ?: hebrewColorName(raw)
