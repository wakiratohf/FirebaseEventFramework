package com.tohsoft.firebase_events.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small date/time helpers used by the SDK. Inlined here so the SDK does not
 * depend on any project-specific utility module.
 */
internal object DateTimeHelper {
    /** Returns [timestamp] formatted with [pattern] in the device's default locale. */
    fun format(timestamp: Long, pattern: String): String {
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
    }

    /** Returns the current hour (0–23, device local time). */
    fun currentHour24(): Int {
        return format(System.currentTimeMillis(), "HH").toIntOrNull() ?: -1
    }
}
