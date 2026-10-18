package com.amir.filamenttracker.presentation.spool

import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import org.junit.Assert.assertEquals
import org.junit.Test

private const val BASE = "למחוק את הסליל? כל ההיסטוריה שלו תימחק, והוא לא ייספר בתצרוכת. אי אפשר לבטל את זה."

class DeleteConfirmTextTest {
    @Test fun `plain stored spool`() =
        assertEquals(BASE, deleteConfirmText(OPEN_STORED, slot = null, orderId = null))

    @Test fun `in use adds the slot`() =
        assertEquals("$BASE חריץ 2 יתפנה.", deleteConfirmText(IN_USE, slot = 2, orderId = null))

    @Test fun `from an order adds the order line`() =
        assertEquals("$BASE הוא יוסר גם מההזמנה.", deleteConfirmText(SEALED, slot = null, orderId = 7))

    @Test fun `in use from an order has both, slot first`() =
        assertEquals("$BASE חריץ 1 יתפנה. הוא יוסר גם מההזמנה.", deleteConfirmText(IN_USE, slot = 1, orderId = 7))
}
