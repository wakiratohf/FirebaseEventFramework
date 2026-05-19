package com.tohsoft.app_event

import android.content.Context
import com.tohsoft.firebase_events.AnalyticsEvents
import com.tohsoft.firebase_events.models.AppExitEv
import com.tohsoft.firebase_events.utils.FirebasePrefs
import com.tohsoft.firebase_events.utils.convertSnakeCaseToCamelCase
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Logs `app_exit` — duration of the foreground session + the last screen the
 * user was on before leaving.
 *
 * Maintains two pieces of internal state:
 * - `lastActiveScreen`: latest screen name set via [setLastActiveScreen]. If
 *   empty/null when [onAppExit] fires, the event is skipped (preserves the
 *   original `AbsLifeCycleApplication` behavior).
 * - `jobExitAppEvent`: debounce job — repeated [onAppExit] calls within 1s
 *   coalesce into a single log (typical when background and process death
 *   fire back-to-back).
 *
 * Both fields are `@Volatile` since callers may invoke from main thread
 * (lifecycle observer) and IO coroutine alike.
 */
@OptIn(DelicateCoroutinesApi::class)
object AppExitTracker {

    @Volatile
    private var lastActiveScreen: String? = null

    @Volatile
    private var jobExitAppEvent: Job? = null

    /**
     * Record the screen the user is currently on. Typically called from your
     * screen-tracking helper (e.g. `ScreenViewEventHelper.logScreenOpen`) or
     * from `AppEventsInstaller`'s internal `onActivityResumed` callback.
     *
     * The last call wins — call after your domain logic has resolved the
     * screen name, so a meaningful name (`"home"`) overrides the Activity
     * class name (`"MainActivity"`) installed by the installer.
     */
    @JvmStatic
    fun setLastActiveScreen(screen: String) {
        lastActiveScreen = screen
    }

    /**
     * Call when the app moves to the background or the session ends. Reads
     * `appOpenedTimestamp` from [FirebasePrefs] (managed by the caller via
     * `saveAppOpenedTimestamp`), computes `duration_sec`, then debounces 1s
     * before logging `app_exit` and flushing the queue.
     *
     * No-op if `appOpenedTimestamp` is unset or `lastActiveScreen` is empty.
     */
    @JvmStatic
    fun onAppExit(context: Context) {
        val timestamp = FirebasePrefs.getAppOpenedTimestamp(context) ?: 0L
        val screen = lastActiveScreen
        if (timestamp == 0L || screen.isNullOrEmpty()) return

        val duration = ((System.currentTimeMillis() - timestamp) / 1000).toInt()
        jobExitAppEvent?.cancel()
        jobExitAppEvent = GlobalScope.launch {
            delay(1000) // debounce: background + kill often fire within < 1s of each other
            AnalyticsEvents.logAppExitEv(
                AppExitEv(
                    duration = duration,
                    lastActiveScreen = screen.convertSnakeCaseToCamelCase(),
                )
            )
            AnalyticsEvents.flushEventsImmediate()
        }
    }
}
