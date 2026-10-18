package com.amir.filamenttracker.presentation.common

import androidx.compose.ui.graphics.Color
import com.amir.filamenttracker.domain.model.SpoolStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

class UiFormatTest {
    @Test fun `short interval reads in days`() { assertEquals("סליל כל ~10 ימים", Duration.ofDays(10).formatInterval()) }
    @Test fun `long interval reads in weeks`() { assertEquals("סליל כל ~3 שבועות", Duration.ofDays(21).formatInterval()) }
    @Test fun `tiny interval reads as daily`() { assertEquals("בערך סליל ביום", Duration.ofHours(5).formatInterval()) }

    @Test fun `singular and plural counts`() {
        assertEquals("1 סגור", countLabel(SpoolStatus.SEALED, 1))
        assertEquals("2 סגורים", countLabel(SpoolStatus.SEALED, 2))
        assertEquals("3 בשימוש", countLabel(SpoolStatus.IN_USE, 3))
    }

    @Test fun `valid hex parses`() { assertEquals(Color(0xFF1A2B3C), parseHexOrNull("#1A2B3C")) }
    @Test fun `broken hex never throws`() {
        assertNull(parseHexOrNull(null))
        assertNull(parseHexOrNull("red"))
        assertNull(parseHexOrNull("#12345"))
        assertNull(parseHexOrNull("#-12345"))
    }
}
