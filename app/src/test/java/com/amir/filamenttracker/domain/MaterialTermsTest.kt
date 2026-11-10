package com.amir.filamenttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialTermsTest {
    private fun parts(variant: String, finish: String = "") = normalizeMaterialParts(variant, finish)

    @Test fun `known words get their canonical spelling`() =
        assertEquals(MaterialParts("", "Silk"), parts("", "SILK"))

    @Test fun `a finish typed as a sub-type moves to the finish`() =
        assertEquals(MaterialParts("", "Translucent"), parts("translucent"))

    @Test fun `a sub-type typed as a finish moves to the sub-type`() =
        assertEquals(MaterialParts("High Speed", "Matte"), parts("", "matte rapid"))

    @Test fun `high speed aliases merge`() {
        listOf("hi speed", "Rapid", "HF", "high-speed", "High Flow").forEach {
            assertEquals(it, MaterialParts("High Speed", ""), parts(it))
        }
    }

    @Test fun `basic means no finish`() = assertEquals(MaterialParts("", ""), parts("Basic"))

    @Test fun `multi-word aliases are matched`() =
        assertEquals(MaterialParts("", "Glow"), parts("", "glow in the dark"))

    @Test fun `known words come in catalog order whatever the typing order`() {
        assertEquals(MaterialParts("High Speed", "Translucent"), parts("Translucent HF"))
        assertEquals(MaterialParts("", "Silk Dual Color"), parts("", "dual color silk"))
    }

    @Test fun `plus comes first and unknown words are kept as typed`() {
        assertEquals(MaterialParts("+ Tough", ""), parts("tough +"))
        assertEquals(MaterialParts("++ High Speed", ""), parts("++ HF"))
        assertEquals(MaterialParts("Pro", "Velvet"), parts("Pro", "Velvet"))
    }

    @Test fun `repeated words are kept once`() =
        assertEquals(MaterialParts("", "Matte"), parts("matte", "Matt"))

    @Test fun `toggling adds a term in catalog order and removes it again`() {
        val added = toggleTerm("Dual Color", "Silk")
        assertEquals("Silk Dual Color", added)
        assertEquals("Dual Color", toggleTerm(added, "Silk"))
    }

    @Test fun `toggling recognizes an alias of the term`() = assertEquals("", toggleTerm("glitter", "Sparkle"))

    @Test fun `hasTerm reads aliases`() {
        assertTrue(hasTerm("rapid", "High Speed"))
        assertFalse(hasTerm("Silk", "Matte"))
    }

    @Test fun `compose puts the finish after the sub-type and attaches a symbol`() {
        assertEquals("PLA+ Matte", composeMaterialName("PLA", "+", "Matte"))
        assertEquals("PETG High Speed Translucent", composeMaterialName("PETG", "High Speed", "Translucent"))
        assertEquals("PLA Silk", composeMaterialName("PLA", "", "Silk"))
        assertEquals("PETG++ High Speed", composeMaterialName("PETG", "++ High Speed"))
    }

    @Test fun `line name is the family and the finish`() {
        assertEquals("PLA", lineName("PLA", ""))
        assertEquals("PLA Matte", lineName("PLA", "Matte"))
    }

    @Test fun `every term is in exactly one chip group`() {
        val all = VARIANT_TERMS + COMMON_FINISH_TERMS + MORE_FINISH_TERMS
        assertEquals(all.size, all.toSet().size)
        assertTrue("Matte" in COMMON_FINISH_TERMS && "Silk" in COMMON_FINISH_TERMS && "Translucent" in COMMON_FINISH_TERMS)
    }
}
