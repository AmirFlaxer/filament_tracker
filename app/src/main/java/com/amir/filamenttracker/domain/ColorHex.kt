package com.amir.filamenttracker.domain

object ColorHex {
    private val pattern = Regex("^#?([0-9A-Fa-f]{6})$")

    /** Blank input means "no swatch" (null). Otherwise "#RRGGBB" in upper case. */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val match = pattern.matchEntire(trimmed)
            ?: throw IllegalArgumentException("גוון לא תקין. יש להזין 6 ספרות הקס, למשל #1A2B3C")
        return "#" + match.groupValues[1].uppercase()
    }
}
