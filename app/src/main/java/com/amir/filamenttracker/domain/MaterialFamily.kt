package com.amir.filamenttracker.domain

data class MaterialKind(val family: String, val variant: String?)

/** Splits a material name at its first space or hyphen: "PLA Matte" and "PLA-CF" give family "PLA" plus a variant. */
fun materialKind(name: String): MaterialKind {
    val parts = name.trim().split(Regex("[\\s-]+"), limit = 2)
    val variant = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
    return MaterialKind(parts[0], variant)
}

/** Comparison key for grouping by family, ignoring case. */
fun familyKey(name: String): String = materialKind(name).family.uppercase()
