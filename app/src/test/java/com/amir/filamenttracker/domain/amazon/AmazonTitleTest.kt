package com.amir.filamenttracker.domain.amazon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmazonTitleTest {
    private val brands = listOf("SUNLU", "ELEGOO", "iSANMATE")
    private val families = listOf("PLA", "PETG")
    private val colors = listOf("Black", "White")

    private fun read(title: String) = readAmazonTitle(title, brands, families, colors)

    private val panchroma = "Panchroma Matte PLA 3D Printer Filament Bundle 2kg, Earth Brown+Wood Brown | 1.75mm High Speed 3D Printing Filament Fits for Most FDM Printers, ±0.02mm Accuracy, Neaty Wound & Matte Finish, 2kg Pack"
    private val sunlu = "SUNLU High Speed PETG Filament 1.75mm, Black+White, 2kg Total, 2-Pack"
    private val elegoo = "ELEGOO Rapid PETG 3D Printer Filament Bundle 1.75mm Black/Gray 2KG"
    private val iemai = "IEMAI High Speed PETG 3D Printer Filament 1.75mm, 600mm/s, 1kg, Black"
    private val isanmate = "iSANMATE Silk PLA 3D Printer Filament Bundle, Shiny Dual Color Filament 1.75mm Pack, Multi Color Change 3D Printing Filament for Most FDM 3D Printer, 4 Spools 1kg Each"
    private val uguess = "UGUESS 10KG PETG 3D Filament Bundle, 1.75mm 10 Colors"
    private val meuymeu = "Meuymeu 6KG PLA+ Filament Bundle, 1.75mm 3D Printer Filament"
    private val xzn = "XZN Silk PLA Filament 1.75mm, Metal-Like Shiny Gold PLA Filament 1kg (2.2lbs) 1 Spool, Dimensional Accuracy +/- 0.02mm, Neatly Wound, No Clogs/Bubbles, Vacuum Sealed, Fit Most FDM Printer, Silk Gold"
    private val qfour = "QFOUR Rainbow PETG Filament 1.75mm, Multicolor Transparent Gradient, 1kg"
    private val chitu = "Chitu Systems PlateCycler C1M for Bambu Lab A1 Mini 3D Printer"

    private fun check(title: String, brand: String, family: String, colors: List<String>, spools: Int) {
        val r = read(title)
        assertEquals(brand, r.brand)
        assertEquals(family, r.family)
        assertEquals(colors, r.colors)
        assertEquals(spools, r.spoolsPerUnit)
        assertEquals(1000, r.weightGrams)
        assertTrue(isFilamentTitle(title, families))
    }

    @Test fun panchroma() = check(panchroma, "Panchroma", "PLA", listOf("Earth Brown", "Wood Brown"), 2)
    @Test fun sunlu() = check(sunlu, "SUNLU", "PETG", listOf("Black", "White"), 2)
    @Test fun elegoo() = check(elegoo, "ELEGOO", "PETG", listOf("Black", "Gray"), 2)
    @Test fun iemai() = check(iemai, "IEMAI", "PETG", listOf("Black"), 1)
    @Test fun isanmate() = check(isanmate, "iSANMATE", "PLA", emptyList(), 4)
    @Test fun uguess() = check(uguess, "UGUESS", "PETG", emptyList(), 10)
    @Test fun meuymeu() = check(meuymeu, "Meuymeu", "PLA", emptyList(), 6)
    @Test fun xzn() = check(xzn, "XZN", "PLA", listOf("Gold"), 1)

    @Test fun `marketing after the pipe is ignored`() {
        val r = read(panchroma)
        assertEquals("Matte", r.finish)
        assertEquals("", r.variant)
    }

    @Test fun `PLA plus is a variant, not a color`() {
        val r = read(meuymeu)
        assertEquals("+", r.variant)
        assertTrue(r.colors.isEmpty())
    }

    @Test fun `plus-minus is not a color list`() {
        assertTrue(read("ACME PLA Filament, +/-0.02mm, 1kg").colors.isEmpty())
    }

    @Test fun `dual color with listed colors is one combined spool`() {
        val r = read("ACME Silk PLA Dual Color Red/Gold 1kg")
        assertTrue(r.combined)
        assertEquals(listOf("Red", "Gold"), r.colors)
        assertEquals(1, r.spoolsPerUnit)
    }

    @Test fun `two colors without a pack hint are two spools`() {
        assertEquals(2, read("ACME PLA Filament, Black+White").spoolsPerUnit)
    }

    @Test fun `multicolor transparent gradient is not a color`() {
        val r = read(qfour)
        assertTrue(r.colors.isEmpty())
        assertEquals(1, r.spoolsPerUnit)
    }

    @Test fun `finish glued to the color list is kept`() {
        val r = read("ACME Matte Black/White PLA 1kg")
        assertEquals("Matte", r.finish)
        assertEquals(listOf("Black", "White"), r.colors)
    }

    @Test fun `color list does not start mid-word`() {
        val r = read("iSANMATE PLA Black/White 2kg")
        assertEquals("iSANMATE", r.brand)
        assertEquals(listOf("Black", "White"), r.colors)
    }

    @Test fun `half kilo spool`() {
        assertEquals(500, read("ACME PLA Filament 0.5kg Black").weightGrams)
    }

    @Test fun `non filament`() {
        assertFalse(isFilamentTitle(chitu, families))
    }
}
