package com.tohsoft.firebase_events.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DateTimeHelperTest {

    @Test
    fun `format produces a non-empty string for valid pattern`() {
        val out = DateTimeHelper.format(0L, "yyyy")
        assertNotNull(out)
        assertEquals(4, out.length)
    }

    @Test
    fun `format respects two-digit hour pattern`() {
        // Epoch 0 = 1970-01-01 00:00:00 UTC. Local-time hour may differ — just
        // verify the format is 2 digits.
        val out = DateTimeHelper.format(0L, "HH")
        assertEquals(2, out.length)
        assertTrue(out.toInt() in 0..23)
    }

    @Test
    fun `currentHour24 returns 0 to 23`() {
        val hour = DateTimeHelper.currentHour24()
        assertTrue("hour was $hour", hour in 0..23)
    }
}
