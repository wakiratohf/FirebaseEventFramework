# app_exit — Integration guide

Logs **how long** the user spent in the foreground and **which screen** they
were on before leaving.

> Module split:
> - `:firebase-events` owns the Firebase log:
>   [`AnalyticsEvents.logAppExitEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsEvents.kt),
>   [`AppExitEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/models/AppExitEv.kt)
>   formatter, `app_exit_ev_enable` remote-config flag.
> - `:app-event` (this module) owns the **Android lifecycle plumbing**:
>   tracking the active screen, computing the session duration, debouncing
>   back-to-back triggers, flushing the event queue on exit. Helpers:
>   [`AppExitTracker`](../src/main/java/com/tohsoft/app_event/AppExitTracker.kt)
>   and [`AppEventsInstaller`](../src/main/java/com/tohsoft/app_event/AppEventsInstaller.kt).

---

## What gets logged

Event name: `app_exit`

| Param | Type | Example | Notes |
|---|---|---|---|
| `duration_sec` | int | `42` | Seconds between `appOpenedTimestamp` and the exit trigger. |
| `last_active_screen` | string | `"Home"` | Last screen the user was on, in CamelCase (passed through `convertSnakeCaseToCamelCase`). |

The event is **suppressed** when `appOpenedTimestamp` is `0` or
`lastActiveScreen` was never set during this session. This matches the original
`AbsLifeCycleApplication` behavior — sessions that never reached a real screen
(splash dismissed instantly, ad activity only) are not counted.

---

## Pattern A — `AppEventsInstaller.install()` (RECOMMENDED, 1 line)

The installer registers its own lifecycle observer and tags
`last_active_screen` with each Activity's class simpleName as the user moves
between screens. Android allows multiple `ActivityLifecycleCallbacks` to
coexist, so your existing callbacks (analytics, ads, anti-tampering, …) keep
firing alongside.

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppEventsInstaller.install(this)
    }
}
```

### Custom screen mapping

Skip splash/ad activities so they don't end up as `last_active_screen`, or
map class names to your domain-level screen names:

```kotlin
AppEventsInstaller.install(this) { activity ->
    when (activity) {
        is SplashActivity, is AdActivity -> null            // keep previous screen
        is MainActivity -> "home"                           // domain-level name
        else -> activity.javaClass.simpleName               // fallback
    }
}
```

Returning `null` for an activity preserves the previously-set screen name —
useful when the user goes through a brief overlay you don't want to attribute
the exit to.

### Override from your domain screen tracker

`AppExitTracker.setLastActiveScreen(...)` can also be called manually. The
**last call wins**, so calling from your screen helper after the installer's
`onActivityResumed` lets you replace `"MainActivity"` with `"home"`:

```kotlin
fun logScreenOpen(screenName: String) {
    AnalyticsUserProperties.logEventScreenOpen(screenName, "")
    AppExitTracker.setLastActiveScreen(screenName) // domain name wins
}
```

---

## Pattern B — Explicit calls via `ProcessLifecycleOwner`

For apps that need to inspect state before logging or want to fire on a
trigger besides `ON_STOP`.

```kotlin
class MyApp : Application(), LifecycleEventObserver {
    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_STOP) {
            AppExitTracker.onAppExit(this)
        }
    }
}
```

Still call `AppExitTracker.setLastActiveScreen(name)` from your screen helper
or per-activity `onResume`.

---

## Pattern C — Explicit calls via `ActivityLifecycleCallbacks` (count-based)

```kotlin
class MyApp : Application(), Application.ActivityLifecycleCallbacks {
    private var startedCount = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedCount++
    }

    override fun onActivityStopped(activity: Activity) {
        if (--startedCount == 0) {
            AppExitTracker.onAppExit(this)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        AppExitTracker.setLastActiveScreen(activity::class.java.simpleName)
    }

    // ... other callbacks empty
}
```

---

## Pattern D — Hybrid (production example: `toh-weather`)

`toh-weather` calls `AppExitTracker.onAppExit` from two places:

1. `ProcessLifecycleOwner.ON_STOP` (normal background).
2. `endSession()` after all activities have been destroyed (clean shutdown).

Plus a flush of pending analytics events before exit:

```kotlin
override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    if (event == Lifecycle.Event.ON_STOP) {
        AnalyticsEvents.flushEventsImmediate()
        if (ActivityUtils.getActivityList().isEmpty()) endSession()
        AppExitTracker.onAppExit(applicationContext)
    }
}

private fun endSession() {
    isMoveToBackground = true
    AppExitTracker.onAppExit(applicationContext)
    setAppOpenedTimestamp(0L)
    NotificationFullScreenActivity.clearCache(this)
}
```

See `app/src/main/java/com/tohsoft/weather/AbsLifeCycleApplication.kt` for
the complete production wiring.

---

## Important — do not combine the installer with explicit calls

`AppEventsInstaller.install` already fires `onAppExit` on `ON_STOP`. Calling
it again from your own observer will produce **one log within the 1s debounce
window, two if your trigger fires > 1s later**. Pick one pattern.

---

## Lifecycle: when does this fire?

| Scenario | Fires? | Notes |
|---|---|---|
| User presses Home / Recent button | ✅ | `ON_STOP` after 700 ms grace, then 1s debounce. |
| User swipes app from Recents | ✅ | Both `ON_STOP` and process death may fire; debounce coalesces to one log. |
| Phone call interrupts foreground briefly (< 1 s) | ⚠️ | `ON_STOP` fires; if user returns quickly, the new `ON_START` does not cancel the queued exit. Acceptable tradeoff — the 1s debounce only protects against same-class duplicates. |
| Configuration change (rotation) | ❌ | `ProcessLifecycleOwner` does not transition through `ON_STOP`. |
| `lastActiveScreen` never set (session ended on splash) | ❌ | Suppressed by design. |
| `appOpenedTimestamp` never set | ❌ | Suppressed by design (host didn't mark session start). |

---

## Required: set `appOpenedTimestamp`

`AppExitTracker.onAppExit` computes `duration_sec` as
`now - FirebasePrefs.getAppOpenedTimestamp(context)`. The host app is
responsible for **calling `FirebasePrefs.saveAppOpenedTimestamp(context, ts)`
when the session starts** (e.g. in `Application.onCreate` or when MainActivity
is first created). If you forget, every `app_exit` log is suppressed
(`timestamp == 0L` short-circuits).

This intentionally lives outside the tracker — different apps mark session
start at different points (first activity vs. app open vs. user login), and
the tracker doesn't try to guess.

---

## Common pitfalls

**Setting `lastActiveScreen` only in `onResume` of one base class.** If part
of your screens don't go through that base class, they show as the *previous*
screen on exit. Either route every screen through the helper or use the
installer (which tags from `onActivityResumed` for every Activity uniformly).

**Reusing `last_active_screen` for funnel analysis.** This event captures the
**last** screen, not a path. For path analysis, use `screen_view_ev` (in
`:firebase-events`).

**Debounce window not matching your trigger cadence.** The 1s debounce
catches `ON_STOP` + process-death pairs. If your custom trigger fires the
exit > 1s after `ON_STOP`, both will log. Tighten by gating in caller.

---

## Reference

- API: [`AppExitTracker`](../src/main/java/com/tohsoft/app_event/AppExitTracker.kt),
  [`AppEventsInstaller`](../src/main/java/com/tohsoft/app_event/AppEventsInstaller.kt).
- Firebase log layer: [`AnalyticsEvents.logAppExitEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsEvents.kt),
  [`AppExitEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/models/AppExitEv.kt).
- Remote-config toggle: `app_exit_ev_enable` in `EventConfigs`.
