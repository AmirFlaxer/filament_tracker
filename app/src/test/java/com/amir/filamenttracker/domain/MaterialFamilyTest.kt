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
}
