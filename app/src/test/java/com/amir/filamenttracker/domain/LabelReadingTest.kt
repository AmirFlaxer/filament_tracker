package com.amir.filamenttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelReadingTest {
    private val XZN = """
        XZN
        PLA 1.75mm Color: Silk Gold
        700-001-1216 BTBG0438C6
        Printing Temp : 195°C-230°C
        Bed Temp : 30°C-60°C
        Net weight : 1KG
        MADE IN CHINA
    """.trimIndent()

    @Test fun `xzn label reads brand family finish and color`() {
        val r = readLabel(XZN, knownBrands = listOf("iSANMATE"), knownFamilies = listOf("PLA", "PETG"), knownColors = listOf("שחור", "זהב"))
        assertEquals(LabelReading(brand = "XZN", family = "PLA", variant = "", finish = "Silk", colors = setOf(colorKey("gold"))), r)
    }

    @Test fun `known brand wins over first line`() {
        val r = readLabel("Sunlu\nPETG Black", listOf("SUNLU"), listOf("PLA", "PETG"), emptyList())
        assertEquals("SUNLU", r.brand)
        assertEquals("PETG", r.family)
        assertEquals(setOf(colorKey("black")), r.colors)
    }

    @Test fun `longest known brand wins`() {
        val r = readLabel("Bambu Lab PLA", listOf("Bambu", "Bambu Lab"), listOf("PLA"), emptyList())
        assertEquals("Bambu Lab", r.brand)
    }

    @Test fun `plus attaches to family`() {
        val r = readLabel("PLA+ Matte Black", emptyList(), listOf("PLA"), emptyList())
        assertEquals("PLA", r.family)
        assertEquals("+", r.variant)
        assertEquals("Matte", r.finish)
    }

    @Test fun `clear after color is not a finish`() {
        val r = readLabel("PLA Color: Clear", emptyList(), listOf("PLA"), emptyList())
        assertEquals("", r.finish)
        assertEquals("Transparent", readLabel("PLA Clear", emptyList(), listOf("PLA"), emptyList()).finish)
    }

    @Test fun `short terms count only as whole words`() {
        assertEquals("", readLabel("PLA ahs hsm", emptyList(), listOf("PLA"), emptyList()).variant)
        assertEquals("High Speed", readLabel("PLA HS", emptyList(), listOf("PLA"), emptyList()).variant)
    }

    @Test fun `hebrew and english color names share a key`() {
        assertEquals(colorKey("שחור"), colorKey("Black"))
        assertEquals("foo", colorKey(" Foo "))
    }

    @Test fun `generic brand spellings`() {
        listOf("גנרי", " Generic ", "").forEach { assertTrue(isGenericBrand(it)) }
        assertFalse(isGenericBrand("XZN"))
    }

    @Test fun `empty text reads nothing`() {
        assertEquals(LabelReading(null, null, "", "", emptySet()), readLabel("", emptyList(), listOf("PLA"), emptyList()))
    }

    @Test fun `hyphenated codes are not terms`() {
        val r = readLabel("PLA HS-1216 TRI-0438 Black", emptyList(), listOf("PLA"), emptyList())
        assertEquals("", r.variant)
        assertEquals("", r.finish)
    }

    @Test fun `carbon black is a color not carbon fiber`() {
        val r = readLabel("PLA Carbon Black", emptyList(), listOf("PLA"), emptyList())
        assertEquals("", r.variant)
        assertTrue(colorKey("black") in r.colors)
        assertEquals("CF", readLabel("PLA Carbon", emptyList(), listOf("PLA"), emptyList()).variant)
    }

    @Test fun `multi word known color matches as a phrase`() {
        val r = readLabel("PLA Sky Blue", emptyList(), listOf("PLA"), listOf("Sky Blue"))
        assertEquals(setOf(colorKey("Sky Blue")), r.colors)
    }

    @Test fun `material codes with a digit keep their terms`() {
        listOf("PA6-CF Black", "PA12-CF Black").forEach {
            assertEquals("CF", readLabel(it, emptyList(), listOf("PA", "PLA"), emptyList()).variant)
        }
        assertEquals("", readLabel("PLA HS-1216 TRI-0438 Black", emptyList(), listOf("PA", "PLA"), emptyList()).variant)
    }
}
