package com.tohsoft.firebase_events.models

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventConfigsTest {

    private val gson = Gson()

    @Test
    fun `defaults are all true`() {
        val cfg = EventConfigs()
        assertTrue(cfg.iapEvEnable)
        assertTrue(cfg.showRateDialogEvEnable)
        assertTrue(cfg.timeOpenAppEvEnable)
        assertTrue(cfg.loadAdEvEnable)
        assertTrue(cfg.showAdEvEnable)
        assertTrue(cfg.clickAdEvEnable)
        assertTrue(cfg.openAppFromEvEnable)
        assertTrue(cfg.screenViewEvEnable)
        assertTrue(cfg.clickBtnEvEnable)
        assertTrue(cfg.paidAdImpressionEvEnable)
        assertTrue(cfg.appExitEvEnable)
        assertTrue(cfg.onboardingStepEvEnable)
    }

    @Test
    fun `Gson roundtrip preserves all flags`() {
        val before = EventConfigs().apply {
            iapEvEnable = false
            screenViewEvEnable = false
            clickBtnEvEnable = false
        }
        val json = gson.toJson(before)
        val after = gson.fromJson(json, EventConfigs::class.java)

        assertEquals(before.iapEvEnable, after.iapEvEnable)
        assertEquals(before.screenViewEvEnable, after.screenViewEvEnable)
        assertEquals(before.clickBtnEvEnable, after.clickBtnEvEnable)
        // Untouched flag should still be true after roundtrip.
        assertTrue(after.showAdEvEnable)
    }

    @Test
    fun `Gson serialisation uses snake_case keys`() {
        val json = gson.toJson(EventConfigs())
        assertTrue("missing iap_ev_enable key in $json", json.contains("\"iap_ev_enable\""))
        assertTrue("missing screen_view_ev_enable key in $json", json.contains("\"screen_view_ev_enable\""))
        assertTrue("missing onboarding_step_enable key in $json", json.contains("\"onboarding_step_enable\""))
    }

    @Test
    fun `Gson can parse partial JSON and keep defaults`() {
        val json = """{"iap_ev_enable": false}"""
        val cfg = gson.fromJson(json, EventConfigs::class.java)
        assertEquals(false, cfg.iapEvEnable)
        // Other flags should still default to true.
        assertTrue(cfg.screenViewEvEnable)
        assertTrue(cfg.clickBtnEvEnable)
    }
}
