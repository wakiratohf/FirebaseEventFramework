package com.tohsoft.firebase_events.models

import org.junit.Assert.assertEquals
import org.junit.Test

class AdResultTest {

    @Test
    fun `SUCCESS matches the legacy string`() {
        // ShowAdEv.toBundle compares against AdResult.SUCCESS to produce
        // "1" vs "0" — if this value ever drifts, the show_ad_ev result
        // param will silently flip for every caller, so guard it.
        assertEquals("success", AdResult.SUCCESS)
    }

    @Test
    fun `FAILED matches the legacy string`() {
        assertEquals("failed", AdResult.FAILED)
    }
}