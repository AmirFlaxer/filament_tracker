package com.amir.filamenttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSwatchTest {
    @Test fun `hebrew and english names give the palette hex`() {
        assertEquals(ColorSwatch.Hex("#000000"), resolveSwatch("שחור", null))
        assertEquals(ColorSwatch.Hex("#D32F2F"), resolveSwatch("Red", null))
        assertEquals(ColorSwatch.Hex("#808080"), resolveSwatch("grey", null))
        assertEquals(ColorSwatch.Hex("#808080"), resolveSwatch("אפור", null))
        assertEquals(ColorSwatch.Hex("#CD7F32"), resolveSwatch("ברונזה", null))
    }

    @Test fun `name match ignores case and whitespace`() {
        assertEquals(ColorSwatch.Hex("#1E5BD8"), resolveSwatch("  BLUE ", null))
    }

    @Test fun `all twelve palette colors resolve`() {
        val names = listOf("שחור", "לבן", "אדום", "צהוב", "כתום", "כחול", "סגול", "ברונזה", "ורוד", "זהב", "אפור", "ירוק")
        names.forEach { assertEquals(it, true, resolveSwatch(it, null) is ColorSwatch.Hex) }
    }

    @Test fun `clear and multicolor are markers`() {
        assertEquals(ColorSwatch.Clear, resolveSwatch("שקוף", null))
        assertEquals(ColorSwatch.Clear, resolveSwatch("Transparent", null))
        assertEquals(ColorSwatch.Clear, resolveSwatch("clear", null))
        assertEquals(ColorSwatch.Multi, resolveSwatch("מולטיקולור", null))
        assertEquals(ColorSwatch.Multi, resolveSwatch("Rainbow", null))
        assertEquals(ColorSwatch.Multi, resolveSwatch("Multicolor", null))
    }

    @Test fun `unknown name without hex is none`() {
        assertEquals(ColorSwatch.None, resolveSwatch("Teal", null))
        assertEquals(ColorSwatch.None, resolveSwatch("", null))
    }

    @Test fun `stored hex wins over the name`() {
        assertEquals(ColorSwatch.Hex("#112233"), resolveSwatch("Red", "#112233"))
        assertEquals(ColorSwatch.Hex("#112233"), resolveSwatch("Teal", "112233"))
        assertEquals(ColorSwatch.Hex("#112233"), resolveSwatch("Clear", "#112233"))
    }

    @Test fun `invalid stored hex falls back to the name`() {
        assertEquals(ColorSwatch.Hex("#D32F2F"), resolveSwatch("Red", "nope"))
        assertEquals(ColorSwatch.None, resolveSwatch("Teal", "#12345"))
    }

    @Test fun `resolveColorHex gives hex only for real colors`() {
        assertEquals("#D32F2F", resolveColorHex("Red", null))
        assertEquals("#112233", resolveColorHex("Red", "#112233"))
        assertEquals(null, resolveColorHex("Clear", null))
        assertEquals(null, resolveColorHex("Teal", null))
    }

    @Test fun `light colors get the dark background and the rest the light one`() {
        val light = listOf("#000000", "#D32F2F", "#1E5BD8", "#8E24AA", "#E91E63", "#808080", "#2E7D32")
        val dark = listOf("#FFFFFF", "#FBC02D", "#F57C00", "#D4AF37", "#CD7F32")
        light.forEach { assertEquals(it, ChipBackground.LIGHT, chipBackgroundFor(it)) }
        dark.forEach { assertEquals(it, ChipBackground.DARK, chipBackgroundFor(it)) }
    }

    @Test fun `halo only for names hard to read on the surface`() {
        val light = "#FFFBFE"
        val dark = "#1C1B1F"
        listOf("#FFFFFF", "#FBC02D").forEach { assertEquals(it, true, needsHalo(it, light)) }
        listOf("#000000", "#D32F2F", "#8E24AA").forEach { assertEquals(it, false, needsHalo(it, light)) }
        listOf("#000000", "#8E24AA").forEach { assertEquals(it, true, needsHalo(it, dark)) }
        listOf("#FFFFFF", "#FBC02D").forEach { assertEquals(it, false, needsHalo(it, dark)) }
    }
}
