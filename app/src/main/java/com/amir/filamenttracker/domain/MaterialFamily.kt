package com.amir.filamenttracker.domain

data class MaterialKind(val family: String, val variant: String?)

private val BASE_MATERIALS = setOf("PLA", "PETG", "ABS", "ASA", "TPU", "PA", "PC", "PET", "PVA", "HIPS")

/**
 * Splits a material name into words at spaces and hyphens. The family is the first word that is a
 * known base material ("Rapid PETG" is PETG), or else the first word; the other words are the variant.
 */
fun materialKind(name: String): MaterialKind {
    val words = name.trim().split(Regex("[\\s-]+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return MaterialKind("", null)
    val index = words.indexOfFirst { it.uppercase() in BASE_MATERIALS }.takeIf { it >= 0 } ?: 0
    val variant = (words.take(index) + words.drop(index + 1)).joinToString(" ").takeIf { it.isNotEmpty() }
    return MaterialKind(words[index], variant)
}

/** Comparison key for grouping by family, ignoring case. */
fun familyKey(name: String): String = materialKind(name).family.uppercase()
