package com.tohsoft.firebase_events.utils

import android.os.Bundle
import android.util.Log

/**
 * Soft validation for Firebase Analytics name/value constraints.
 *
 * Firebase silently drops oversized events instead of crashing, which is a
 * common source of "we logged the event but it didn't show up in DebugView"
 * bugs. This validator logs a warning the first time it sees a violation so
 * the issue surfaces during development. It never throws and never blocks
 * the event — production telemetry is best-effort.
 *
 * Only runs when `AnalyticsModule.isTestMode == true` to keep release builds
 * silent.
 *
 * Limits per the Firebase Analytics docs
 * (https://support.google.com/firebase/answer/9237506):
 * - Event name: ≤ 40 chars, must match `[A-Za-z][A-Za-z0-9_]*`
 * - Param key: ≤ 40 chars
 * - Param string value: ≤ 100 chars
 */
internal object EventNameValidator {
    private const val TAG = "AnalyticsValidator"
    private const val EVENT_NAME_MAX = 40
    private const val PARAM_KEY_MAX = 40
    private const val PARAM_VALUE_MAX = 100
    private val EVENT_NAME_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*$")

    /** Warn (don't throw) on violations. Returns true if the event looks valid. */
    fun validate(eventName: String, params: Bundle?): Boolean {
        var ok = true
        if (eventName.length > EVENT_NAME_MAX) {
            Log.w(TAG, "Event name '$eventName' is ${eventName.length} chars (max $EVENT_NAME_MAX). Firebase will drop it.")
            ok = false
        }
        if (!EVENT_NAME_REGEX.matches(eventName)) {
            Log.w(TAG, "Event name '$eventName' fails Firebase regex [A-Za-z][A-Za-z0-9_]*.")
            ok = false
        }
        params?.keySet()?.forEach { key ->
            if (key.length > PARAM_KEY_MAX) {
                Log.w(TAG, "Param key '$key' on '$eventName' is ${key.length} chars (max $PARAM_KEY_MAX).")
                ok = false
            }
            val value = params.getString(key)
            if (value != null && value.length > PARAM_VALUE_MAX) {
                Log.w(TAG, "Param value for '$key' on '$eventName' is ${value.length} chars (max $PARAM_VALUE_MAX).")
                ok = false
            }
        }
        return ok
    }
}
