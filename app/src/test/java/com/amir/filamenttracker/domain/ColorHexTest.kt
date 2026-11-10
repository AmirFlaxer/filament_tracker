package com.amir.filamenttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorHexTest {
    @Test fun `blank means no swatch`() { assertNull(ColorHex.normalize("  ")) }
    @Test fun `adds hash and upper-cases`() { assertEquals("#1A2B3C", ColorHex.normalize("1a2b3c")) }
    @Test fun `keeps hash and trims`() { assertEquals("#ABCDEF", ColorHex.normalize(" #abcdef ")) }
    @Test(expected = IllegalArgumentException::class) fun `color word is rejected`() { ColorHex.normalize("red") }
    @Test(expected = IllegalArgumentException::class) fun `five digits are rejected`() { ColorHex.normalize("#12345") }
    @Test(expected = IllegalArgumentException::class) fun `seven digits are rejected`() { ColorHex.normalize("#1234567") }
    @Test fun `a combined color keeps one hue per part`() {
        assertEquals("#D32F2F #D4AF37", ColorHex.normalize("#d32f2f, d4af37"))
        assertEquals("#D32F2F #D4AF37 #000000", ColorHex.normalize("#D32F2F / #D4AF37 / #000000"))
    }
    @Test(expected = IllegalArgumentException::class) fun `a bad part rejects the whole list`() { ColorHex.normalize("#D32F2F red") }
}
