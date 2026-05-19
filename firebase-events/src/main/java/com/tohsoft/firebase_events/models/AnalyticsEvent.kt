package com.tohsoft.firebase_events.models

import android.os.Bundle

/**
 * Optional contract for project-defined events.
 *
 * Implement this interface when you need to ship a new event type from the
 * host app without modifying the SDK. Pass the instance to
 * [com.tohsoft.firebase_events.AnalyticsEvents.logEvent] (overload that takes
 * an [AnalyticsEvent]) and the SDK will forward the name and bundle to
 * Firebase Analytics, honouring [com.tohsoft.firebase_events.AnalyticsModule.isEnabled]
 * and the test-log modes the same way as the built-in events.
 *
 * The SDK's built-in event data classes ([_ScreenViewEv], [_ClickBtnEv],
 * [ShowAdEv], [IAPEv], ...) intentionally do NOT implement this interface so
 * that their public APIs stay backward-compatible. New events should
 * implement it.
 *
 * Firebase limits to remember:
 * - event name: ≤ 40 chars, `[A-Za-z][A-Za-z0-9_]*`
 * - param key: ≤ 40 chars
 * - param string value: ≤ 100 chars
 */
interface AnalyticsEvent {
    val eventName: String
    fun toBundle(): Bundle
}
