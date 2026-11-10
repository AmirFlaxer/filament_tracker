package com.amir.filamenttracker.domain

object ColorHex {
    private val pattern = Regex("^#?([0-9A-Fa-f]{6})$")

    /**
     * Blank input means "no swatch" (null). Otherwise "#RRGGBB" in upper case, or for a combined color
     * (two or three colors in one spool) one such hue per part, separated by spaces.
     */
    fun normalize(input: String): String? {
        val parts = input.split(Regex("[\\s,/]+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return parts.joinToString(" ") { part ->
            val match = pattern.matchEntire(part)
                ?: throw IllegalArgumentException("גוון לא תקין. יש להזין 6 ספרות הקס, למשל #1A2B3C")
            "#" + match.groupValues[1].uppercase()
        }
    }
}
