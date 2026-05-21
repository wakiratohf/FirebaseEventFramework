package com.tohsoft.app_event

import android.content.Context
import com.tohsoft.firebase_events.AnalyticsEvents
import com.tohsoft.firebase_events.models.TimeOpenAppEv
import com.tohsoft.firebase_events.utils.FirebasePrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logs `time_open_app_ev` — captures the hour the user opened the app and the
 * delta (in minutes) since the previous open.
 *
 * Lifecycle-agnostic: caller decides when "session start" means. See
 * `docs/TIME_OPEN_APP_GUIDE.md` for integration patterns (high-level
 * `AppEventsInstaller.install` or explicit calls from your own lifecycle code).
 */
object TimeOpenAppTracker {

    /**
     * Call when the app starts a new foreground session. Reads the previous
     * open timestamp from [FirebasePrefs], computes hour/delta, logs the
     * event, then persists the new timestamp.
     */
    @JvmStatic
    fun onSessionStart(context: Context) {
        val now = System.currentTimeMillis()
        val last = FirebasePrefs.getLastTimeOpenApp(context)
        val currentHour = hourOf(now)
        val lastHour = if (last != 0L) hourOf(last) else null
        val deltaMinutes = if (last != 0L) (now - last) / 60_000 else null

        AnalyticsEvents.logTimeOpenAppEv(
            TimeOpenAppEv(
                currentLocalTimeInHour = currentHour.toString(),
                lastTimeOpenAppInHour = lastHour?.toString().orEmpty(),
                openAppIntervalInMinute = deltaMinutes?.toString().orEmpty(),
            )
        )
        FirebasePrefs.saveLastTimeOpenApp(context, now)
    }

    private fun hourOf(timestampMs: Long): Int =
        SimpleDateFormat("HH", Locale.getDefault()).format(Date(timestampMs)).toIntOrNull() ?: 0
}
