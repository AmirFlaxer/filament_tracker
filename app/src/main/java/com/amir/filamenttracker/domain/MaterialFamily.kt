package com.amir.filamenttracker.domain

data class MaterialKind(val family: String, val variant: String?)

private val BASE_MATERIALS = setOf("PLA", "PETG", "ABS", "ASA", "TPU", "PA", "PC", "PET", "PVA", "HIPS")

private val TRAILING_SYMBOLS = Regex("^(.*[A-Za-z0-9])([^A-Za-z0-9]+)$")

/**
 * Splits a material name into words at spaces and hyphens. The family is the first word that is a
 * known base material ("Rapid PETG" is PETG), or a known base material followed by a symbol suffix
 * ("PLA+" is PLA with variant "+"), or else the first word; the other words are the variant.
 */
fun materialKind(name: String): MaterialKind {
    val words = name.trim().split(Regex("[\\s-]+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return MaterialKind("", null)
    fun stem(word: String) = TRAILING_SYMBOLS.matchEntire(word)?.groupValues?.get(1)
    val index = words.indexOfFirst { it.uppercase() in BASE_MATERIALS }.takeIf { it >= 0 }
        ?: words.indexOfFirst { w -> stem(w)?.uppercase() in BASE_MATERIALS }.takeIf { it >= 0 }
        ?: 0
    val word = words[index]
    val stem = if (word.uppercase() in BASE_MATERIALS) null else stem(word)?.takeIf { it.uppercase() in BASE_MATERIALS }
    val family = stem ?: word
    val suffix = stem?.let { word.substring(it.length) }
    val variant = (words.take(index) + listOfNotNull(suffix) + words.drop(index + 1))
        .joinToString(" ").takeIf { it.isNotEmpty() }
    return MaterialKind(family, variant)
}

/** Display name: empty variant is the family, a variant without letters or digits is attached ("PLA+"), else spaced. */
fun composeMaterialName(family: String, variant: String): String = when {
    variant.isEmpty() -> family
    variant.none { it.isLetterOrDigit() } -> family + variant
    else -> "$family $variant"
}

/** Comparison key for grouping by family, ignoring case. */
fun familyKey(name: String): String = materialKind(name).family.uppercase()
