package com.amir.filamenttracker.domain

import kotlin.math.pow

/** What to paint for a color: a real hue, the see-through marker, the rainbow marker, or nothing known. */
sealed interface ColorSwatch {
    data class Hex(val hex: String) : ColorSwatch
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

/** A stored hex that is valid always wins; otherwise the name decides. */
fun resolveSwatch(name: String, storedHex: String?): ColorSwatch {
    storedHex?.trim()?.let { hexPattern.matchEntire(it) }?.let { return ColorSwatch.Hex("#" + it.groupValues[1].uppercase()) }
    val key = name.trim().lowercase()
    paletteByName[key]?.let { return ColorSwatch.Hex(it) }
    return when (key) {
        in clearNames -> ColorSwatch.Clear
        in multiNames -> ColorSwatch.Multi
        else -> ColorSwatch.None
    }
}

/** The hex to paint a plain dot with, or null for clear, multicolor and unknown. */
fun resolveColorHex(name: String, storedHex: String?): String? = (resolveSwatch(name, storedHex) as? ColorSwatch.Hex)?.hex

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
