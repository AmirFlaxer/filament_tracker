package com.amir.filamenttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MaterialFamilyTest {
    @Test fun `plain name is its own family`() = assertEquals(MaterialKind("PLA", null), materialKind("PLA"))
    @Test fun `space separates the variant`() = assertEquals(MaterialKind("PLA", "Matte"), materialKind("PLA Matte"))
    @Test fun `hyphen separates the variant`() = assertEquals(MaterialKind("PETG", "CF"), materialKind("PETG-CF"))
    @Test fun `extra spaces are trimmed`() = assertEquals(MaterialKind("PLA", "Silk"), materialKind("  PLA   Silk "))
    @Test fun `plus has no separator`() = assertEquals(MaterialKind("PLA+", null), materialKind("PLA+"))
    @Test fun `family key ignores case`() = assertEquals(familyKey("PLA"), familyKey("pla matte"))
    @Test fun `a known base material later in the name is the family`() =
        assertEquals(MaterialKind("PETG", "Rapid"), materialKind("Rapid PETG"))
    @Test fun `known base material is found case-insensitively and keeps its spelling`() =
        assertEquals(MaterialKind("petg", "Rapid HF"), materialKind("Rapid petg HF"))
    @Test fun `no known base material falls back to the first word`() =
        assertEquals(MaterialKind("Nylon", "X"), materialKind("Nylon X"))
    @Test fun `rapid PETG groups with PETG`() = assertEquals(familyKey("PETG"), familyKey("Rapid PETG"))
}
