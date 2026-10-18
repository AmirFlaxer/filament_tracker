package com.amir.filamenttracker.presentation.inventory

import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import org.junit.Assert.assertEquals
import org.junit.Test

class AddedTextTest {
    @Test fun `one in use names the slot`() =
        assertEquals("נוסף: גנרי PETG לבן, בחריץ 1", addedText("גנרי PETG לבן", 1, IN_USE, 1))
    @Test fun `one sealed`() =
        assertEquals("נוסף: גנרי PLA שחור, סגור", addedText("גנרי PLA שחור", 1, SEALED, null))
    @Test fun `one open stored`() =
        assertEquals("נוסף: גנרי PLA שחור, פתוח באחסון", addedText("גנרי PLA שחור", 1, OPEN_STORED, null))
    @Test fun `several sealed name the status without repeating the count`() =
        assertEquals("נוספו 3: גנרי PLA שחור, סגורים", addedText("גנרי PLA שחור", 3, SEALED, null))
    @Test fun `several open stored name the status without repeating the count`() =
        assertEquals("נוספו 2: גנרי PLA שחור, פתוחים באחסון", addedText("גנרי PLA שחור", 2, OPEN_STORED, null))
}
