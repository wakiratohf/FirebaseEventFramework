package com.tohsoft.firebase_events.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the parts of [EventNameValidator] that don't require a real
 * [android.os.Bundle]. The `params` overload is exercised via the Android
 * instrumentation suite (not yet wired); the name/regex checks below cover
 * the bulk of the value.
 */
class EventNameValidatorTest {

    @Test
    fun `valid event name passes`() {
        assertTrue(EventNameValidator.validate("screen_view_ev", null))
        assertTrue(EventNameValidator.validate("ClickBtnEv", null))
        assertTrue(EventNameValidator.validate("a", null))
    }

    @Test
    fun `event name longer than 40 chars fails`() {
        val name = "a".repeat(41)
        assertFalse(EventNameValidator.validate(name, null))
    }

    @Test
    fun `event name starting with digit fails`() {
        assertFalse(EventNameValidator.validate("1event", null))
    }

    @Test
    fun `event name with dash fails`() {
        assertFalse(EventNameValidator.validate("event-name", null))
    }

    @Test
    fun `event name with space fails`() {
        assertFalse(EventNameValidator.validate("event name", null))
    }

    @Test
    fun `empty event name fails regex`() {
        assertFalse(EventNameValidator.validate("", null))
    }

    @Test
    fun `event name exactly 40 chars passes`() {
        val name = "a".repeat(40)
        assertTrue(EventNameValidator.validate(name, null))
    }
}
