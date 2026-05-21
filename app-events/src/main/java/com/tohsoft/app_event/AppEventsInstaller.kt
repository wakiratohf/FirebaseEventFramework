package com.tohsoft.app_event

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * High-level drop-in for `time_open_app_ev` + `app_exit`.
 *
 * One line in `Application.onCreate()` is enough — the installer registers its
 * own `ActivityLifecycleCallbacks` and `ProcessLifecycleOwner` observer.
 * Android allows multiple lifecycle callbacks to coexist, so this never
 * conflicts with callbacks the host app may already have registered for its
 * own purposes; both fire side by side.
 *
 * Choose this when your app has no special lifecycle requirements. Apps with
 * edge cases (e.g. log `time_open_app_ev` on a dismissed full-screen overlay)
 * should instead call [TimeOpenAppTracker.onSessionStart] /
 * [AppExitTracker.onAppExit] directly. Do **not** combine: using both yields
 * duplicate events.
 *
 * See `docs/TIME_OPEN_APP_GUIDE.md` and `docs/APP_EXIT_GUIDE.md` for the four
 * supported integration patterns (this object is Pattern A).
 */
object AppEventsInstaller {

    @Volatile
    private var installed = false

    /**
     * Install the lifecycle hooks. Idempotent — calling twice is a no-op.
     *
     * @param application host Application; passed to lifecycle observers and
     *   used as [android.content.Context] for `FirebasePrefs`.
     * @param screenNameProvider maps each resumed [Activity] to the
     *   `last_active_screen` value. Returning `null` skips that activity (the
     *   previous screen name is preserved). Defaults to the class simpleName.
     *   Use `null` to filter out splash/ad activities you don't want surfaced
     *   in the `app_exit` event.
     */
    @JvmStatic
    @JvmOverloads
    fun install(
        application: Application,
        screenNameProvider: (Activity) -> String? = { it.javaClass.simpleName },
    ) {
        if (installed) return
        installed = true

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                screenNameProvider(activity)?.let { AppExitTracker.setLastActiveScreen(it) }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_START -> TimeOpenAppTracker.onSessionStart(application)
                    Lifecycle.Event.ON_STOP -> AppExitTracker.onAppExit(application)
                    else -> Unit
                }
            }
        })
    }
}
