package com.tohsoft.firebase_events

import com.tohsoft.firebase_events.utils.WebhookSender
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Regression for the H4 bug found in the diagnose phase: calling
 * [AnalyticsModule.setWebhookSender] used to re-invoke `sender.initialize(...)`
 * each time, so a host that called it twice (cold start + activity-create) saw
 * the Chatango bot send the "login" sentinel event twice per `initAnalytics()`
 * — once from setWebhookSender, once from init().
 *
 * The contract now: setWebhookSender ONLY attaches the sender. Actual transport
 * initialization happens in [AnalyticsModule.init] or [setWebhookInfo].
 */
class AnalyticsModuleWebhookSenderTest {

    private class CountingSender : WebhookSender {
        var initializeCount = 0
        var sendEventCount = 0
        override fun initialize(groupName: String, userName: String, password: String) {
            initializeCount++
        }
        override fun sendEvent(message: String) {
            sendEventCount++
        }
    }

    @After
    fun reset() {
        // No teardown for module state available; tests below must be tolerant of
        // whichever sender a previous test left behind. Each test attaches its own.
    }

    @Test
    fun `setWebhookSender does not call initialize`() {
        val sender = CountingSender()
        AnalyticsModule.setWebhookSender(sender)
        assertEquals(0, sender.initializeCount)
    }

    @Test
    fun `same-instance re-registration is a no-op`() {
        val sender = CountingSender()
        AnalyticsModule.setWebhookSender(sender)
        AnalyticsModule.setWebhookSender(sender)
        AnalyticsModule.setWebhookSender(sender)
        assertEquals("initialize should not be called on registration", 0, sender.initializeCount)
        assertSame(sender, AnalyticsModule.getWebhookSender())
    }

    @Test
    fun `different sender replaces the previous one`() {
        val first = CountingSender()
        val second = CountingSender()
        AnalyticsModule.setWebhookSender(first)
        AnalyticsModule.setWebhookSender(second)
        assertSame(second, AnalyticsModule.getWebhookSender())
        assertEquals(0, first.initializeCount)
        assertEquals(0, second.initializeCount)
    }
}