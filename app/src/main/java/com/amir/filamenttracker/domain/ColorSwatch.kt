package com.amir.filamenttracker.domain

import kotlin.math.pow

/**
 * What to paint for a color: a real hue, the hues of a combined color, the see-through marker, the
 * rainbow marker, or nothing known.
 */
sealed interface ColorSwatch {
    data class Hex(val hex: String) : ColorSwatch
    data class Parts(val hexes: List<String>) : ColorSwatch
    data object Clear : ColorSwatch
    data object Multi : ColorSwatch
    data object None : ColorSwatch
}

private val hexPattern = Regex("^#?([0-9A-Fa-f]{6})$")

/** Names are matched trimmed and lower-cased, in Hebrew and English. */
private val paletteByName: Map<String, String> = mapOf(
    "שחור" to "#000000", "black" to "#000000",
    "לבן" to "#FFFFFF", "white" to "#FFFFFF",
    "אדום" to "#D32F2F", "red" to "#D32F2F",
    "צהוב" to "#FBC02D", "yellow" to "#FBC02D",
    "כתום" to "#F57C00", "orange" to "#F57C00",
    "כחול" to "#1E5BD8", "blue" to "#1E5BD8",
    "סגול" to "#8E24AA", "purple" to "#8E24AA",
    "ברונזה" to "#CD7F32", "bronze" to "#CD7F32",
    "ורוד" to "#E91E63", "pink" to "#E91E63",
    "זהב" to "#D4AF37", "gold" to "#D4AF37",
    "אפור" to "#808080", "gray" to "#808080", "grey" to "#808080",
    "ירוק" to "#2E7D32", "green" to "#2E7D32",
)

private val clearNames = setOf("שקוף", "clear", "transparent")
private val multiNames = setOf("מולטיקולור", "multicolor", "rainbow")

/** A combined color (a dual or tri color spool) is named by its parts: "Red / Gold". */
fun colorParts(name: String): List<String> = name.split('/').map { it.trim() }.filter { it.isNotEmpty() }

fun composeColorName(parts: List<String>): String = parts.joinToString(" / ")

/**
 * Adds [part] to a combined color name, or removes it when it is there (ignoring case). Parts typed
 * by hand stay first; the chosen ones follow the order of [known], so the same colors always give the
 * same name ("Gold / Red" and "Red / Gold" never become two colors).
 */
fun toggleColorPart(current: String, part: String, known: List<String>): String {
    val parts = colorParts(current)
    val present = parts.any { it.equals(part, ignoreCase = true) }
    val chosen = if (present) parts.filterNot { it.equals(part, ignoreCase = true) } else parts + part
    val (listed, typed) = chosen.partition { p -> known.any { it.equals(p, ignoreCase = true) } }
    return composeColorName(typed + listed.sortedBy { p -> known.indexOfFirst { it.equals(p, ignoreCase = true) } })
}

/**
 * A stored hex that is valid always wins; otherwise the name decides. A combined name gets the hue
 * of each part, or the rainbow marker when a part has no known hue.
 */
fun resolveSwatch(name: String, storedHex: String?): ColorSwatch {
    val stored = storedHex?.trim()?.split(Regex("\\s+"))?.mapNotNull { hexPattern.matchEntire(it) }
        ?.map { "#" + it.groupValues[1].uppercase() }.orEmpty()
    when {
        stored.size == 1 -> return ColorSwatch.Hex(stored.single())
        stored.size > 1 -> return ColorSwatch.Parts(stored)
    }
    val parts = colorParts(name)
    if (parts.size > 1) {
        val hexes = parts.map { (resolveSwatch(it, null) as? ColorSwatch.Hex)?.hex }
        return if (hexes.all { it != null }) ColorSwatch.Parts(hexes.filterNotNull()) else ColorSwatch.Multi
    }
    val key = name.trim().lowercase()
    paletteByName[key]?.let { return ColorSwatch.Hex(it) }
    return when (key) {
        in clearNames -> ColorSwatch.Clear
        in multiNames -> ColorSwatch.Multi
        else -> ColorSwatch.None
    }
}

/** The hex to paint a plain dot with (the first part of a combined color), or null for clear, multicolor and unknown. */
fun resolveColorHex(name: String, storedHex: String?): String? = when (val swatch = resolveSwatch(name, storedHex)) {
    is ColorSwatch.Hex -> swatch.hex
    is ColorSwatch.Parts -> swatch.hexes.first()
    else -> null
}

enum class ChipBackground(val hex: String) {
    LIGHT("#EDE7DA"),
    DARK("#3D4A5C"),
}

private fun luminance(hex: String): Double {
    val v = hex.removePrefix("#").toInt(16)
    fun channel(shift: Int): Double {
        val c = ((v shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

private fun contrast(a: String, b: String): Double {
    val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> maxOf(la, lb) to minOf(la, lb) } }
    return (hi + 0.05) / (lo + 0.05)
}

/** WCAG contrast of the text color against each background; the higher wins, a tie goes to LIGHT. */
fun chipBackgroundFor(hex: String): ChipBackground =
    if (contrast(hex, ChipBackground.DARK.hex) > contrast(hex, ChipBackground.LIGHT.hex)) ChipBackground.DARK else ChipBackground.LIGHT

/** True when a name in [textHex] is hard to read (WCAG contrast under 3:1) on a screen of [surfaceHex]. */
fun needsHalo(textHex: String, surfaceHex: String): Boolean = contrast(textHex, surfaceHex) < 3.0
