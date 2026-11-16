package com.amir.filamenttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorNamesTest {
    @Test fun `a compound name is translated with the base color first`() {
        assertEquals("חום אדמה", hebrewColorName("Earth Brown"))
        assertEquals("חום עץ", hebrewColorName("Wood Brown"))
        assertEquals("כחול בהיר", hebrewColorName("Light Blue"))
        assertEquals("ירוק כהה", hebrewColorName("dark green"))
    }

    @Test fun `a single palette color is translated`() {
        assertEquals("שחור", hebrewColorName("Black"))
        assertEquals("אפור", hebrewColorName(" Grey "))
        assertEquals("חום", hebrewColorName("Brown"))
    }

    @Test fun `a name with an unknown word stays as it is`() {
        assertEquals("Galaxy Black", hebrewColorName("Galaxy Black"))
        assertEquals("Earth", hebrewColorName("Earth")) // a descriptor alone is not a color
    }

    @Test fun `a Hebrew name stays as it is`() {
        assertEquals("שחור", hebrewColorName("שחור"))
        assertEquals("חום אדמה", hebrewColorName("חום אדמה"))
    }

    @Test fun `same color across languages`() {
        assertTrue(sameColor("Earth Brown", "חום אדמה"))
        assertTrue(sameColor("Black", "שחור"))
        assertFalse(sameColor("Earth Brown", "Wood Brown"))
    }

    @Test fun `catalog name wins for a color that already exists`() {
        val catalog = listOf("שחור", "Earth Brown")
        assertEquals("שחור", importColorName("Black", catalog))
        assertEquals("Earth Brown", importColorName("Earth Brown", catalog)) // kept as the owner named it
        assertEquals("חום עץ", importColorName("Wood Brown", catalog))
    }

    @Test fun `a compound name gets the hue of its base color`() {
        val brown = resolveColorHex("Brown", null)
        assertEquals(brown, resolveColorHex("Earth Brown", null))
        assertEquals(brown, resolveColorHex("חום עץ", null))
        assertEquals(resolveColorHex("Blue", null), resolveColorHex("Light Blue", null))
        assertEquals(null, resolveColorHex("Galaxy Unknown", null))
    }

    @Test fun `compound names keep separate keys`() {
        assertFalse(colorKey("Earth Brown") == colorKey("Wood Brown"))
    }
}
