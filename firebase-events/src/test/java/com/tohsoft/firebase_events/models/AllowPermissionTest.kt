package com.tohsoft.firebase_events.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AllowPermissionTest {

    @Test
    fun `each identify is a single distinct char`() {
        val identifies = AllowPermission.entries.map { it.identify }
        identifies.forEach { assertEquals("identify '$it' is not 1 char", 1, it.length) }
        assertEquals("identify chars are not unique", identifies.size, identifies.toSet().size)
    }

    @Test
    fun `fromTitle returns the matching enum`() {
        assertEquals(AllowPermission.ALARM, AllowPermission.fromTitle("AlarmPermission"))
        assertEquals(AllowPermission.LOCATION, AllowPermission.fromTitle("LocationPermission"))
        assertEquals(AllowPermission.OVERLAY_PERMISSION, AllowPermission.fromTitle("OverlayPermission"))
    }

    @Test
    fun `fromTitle returns null for unknown title`() {
        assertNull(AllowPermission.fromTitle("NotAPermission"))
    }
}
