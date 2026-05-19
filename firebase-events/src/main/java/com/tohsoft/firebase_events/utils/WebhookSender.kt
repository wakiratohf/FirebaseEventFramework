package com.tohsoft.firebase_events.utils

/**
 * Pluggable transport for the WEBHOOK test-log mode.
 *
 * The SDK does not ship a default implementation; if the host app wants to
 * route test logs to a webhook (Chatango/Slack/Discord/custom HTTP endpoint),
 * it must implement this interface and inject it via
 * [com.tohsoft.firebase_events.AnalyticsModule.setWebhookSender].
 *
 * When no sender is injected, [com.tohsoft.firebase_events.TestLogMode.WEBHOOK]
 * silently drops messages (Telegram mode is unaffected — it uses the built-in
 * [TelegramBot]).
 *
 * Implementations should be thread-safe: [sendEvent] may be called from any
 * thread, including the main thread.
 */
interface WebhookSender {
    /**
     * Configure transport credentials/endpoint. Called once by
     * [com.tohsoft.firebase_events.AnalyticsModule.setWebhookInfo] (and again
     * during [com.tohsoft.firebase_events.AnalyticsModule.init] if the SDK
     * finds saved credentials in prefs).
     *
     * Parameters are intentionally generic — they map onto Chatango
     * `groupName/userName/password`, but a Slack implementation might use
     * them as `webhookUrl/channel/token`, etc.
     */
    fun initialize(groupName: String, userName: String, password: String)

    /** Push a single event-log line. Implementations should never throw. */
    fun sendEvent(message: String)
}
