# time_open_app_ev — Integration guide

Tracks **when** the user opened the app — hour of day, hour of the previous
open, and minutes between the two opens.

> Module split:
> - `:firebase-events` owns the Firebase log:
>   [`AnalyticsEvents.logTimeOpenAppEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsEvents.kt),
>   [`TimeOpenAppEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/models/TimeOpenAppEv.kt)
>   formatter, `time_open_app_ev_enable` remote-config flag.
> - `:app-event` (this module) owns the **Android lifecycle plumbing**:
>   reading the previous open timestamp, computing the delta, persisting the
>   new timestamp. Helpers: [`TimeOpenAppTracker`](../src/main/java/com/tohsoft/app_event/TimeOpenAppTracker.kt)
>   and [`AppEventsInstaller`](../src/main/java/com/tohsoft/app_event/AppEventsInstaller.kt).

---

## What gets logged

Event name: `time_open_app_ev`

| Param | Type | Example | Notes |
|---|---|---|---|
| `time_open_app_ev_current_in_hour` | string | `"14"` | Hour (0–23) when this session started, device local time. |
| `time_open_app_ev_last_in_hour` | string | `"9"` or `""` | Hour of the previous open. Empty on the very first session after install / clear-data. |
| `time_open_app_ev_delta` | string | `"305"` or `""` | Minutes since the previous open. Empty on the first session. |

Persistence is handled in `FirebasePrefs` (key `last_time_open_app`) — survives
process death and is shared across modules.

---

## Pattern A — `AppEventsInstaller.install()` (RECOMMENDED, 1 line)

For apps that have **no special lifecycle requirements** and want zero-touch
integration. The installer registers its own `ActivityLifecycleCallbacks` +
`ProcessLifecycleOwner` observer, which **live side by side** with any
callbacks the host app may already have registered for its own purposes
(Android invokes each callback in registration order).

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppEventsInstaller.install(this)
    }
}
```

That's it. `time_open_app_ev` fires on every `ProcessLifecycleOwner.ON_START`
(i.e. when the app enters foreground). Customize via the optional second
argument — see [`APP_EXIT_GUIDE.md`](APP_EXIT_GUIDE.md) (the screen-mapping
parameter is shared between the two events).

---

## Pattern B — Explicit calls via `ProcessLifecycleOwner`

For apps that want explicit control of when the event fires (e.g. add a
condition, log on an extra trigger besides `ON_START`).

```kotlin
class MyApp : Application(), LifecycleEventObserver {
    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_START) {
            TimeOpenAppTracker.onSessionStart(this)
        }
    }
}
```

---

## Pattern C — Explicit calls via `ActivityLifecycleCallbacks` (count-based)

For apps that do not want a dependency on `androidx.lifecycle:lifecycle-process`
in their own code (the module already ships it; this is purely a code-style
preference).

```kotlin
class MyApp : Application(), Application.ActivityLifecycleCallbacks {
    private var startedCount = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        if (startedCount++ == 0) {
            TimeOpenAppTracker.onSessionStart(this)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount--
    }

    // ... other callbacks empty
}
```

---

## Pattern D — Hybrid (production example: `toh-weather`)

For apps with edge cases — e.g. logging `time_open_app_ev` on full-screen
overlay dismiss in addition to the normal `ON_START` trigger. `toh-weather`
calls `TimeOpenAppTracker.onSessionStart` from two places:

1. `ProcessLifecycleOwner.ON_START` (normal foreground).
2. `Application.ActivityLifecycleCallbacks.onActivityCreated` — when
   `MainActivity` starts AND a `NotificationFullScreenActivity` was the
   previously-stopped activity (the user came back from the overlay).

```kotlin
// In ProcessLifecycleOwner observer
if (event == Lifecycle.Event.ON_START && appOpenedTimestamp > 0) {
    TimeOpenAppTracker.onSessionStart(applicationContext)
}

// In ActivityLifecycleCallbacks.onActivityCreated
if (activity is MainActivity && isFullScreenNotificationDialogStopped && /* ... */) {
    TimeOpenAppTracker.onSessionStart(applicationContext)
}
```

See `app/src/main/java/com/tohsoft/weather/AbsLifeCycleApplication.kt` for the
complete production wiring.

---

## Important — do not combine the installer with explicit calls

If you call `AppEventsInstaller.install(this)` **and** also fire
`TimeOpenAppTracker.onSessionStart()` from your own observer, the event will be
logged twice per session. Pick one integration pattern and stick with it.

---

## Lifecycle: when does this fire?

| Scenario | Fires? | Notes |
|---|---|---|
| Cold start from launcher icon | ✅ first session — `delta=""` | Installer-pattern: `ON_START` fires after `onCreate`. |
| Cold start from notification / deep link | ✅ first session — `delta=""` | Same `ON_START`. |
| Background → resume after 5 min | ✅ `delta=5` | A new `ON_START` event. |
| Rotation / configuration change | ❌ | Activity recreated but `ProcessLifecycleOwner.ON_START` does **not** re-fire (process never left foreground). |
| Quick activity switch within the same task | ❌ | Same reason — no new `ON_START`. |

For "user came back to the app **regardless of how**" use this event. For
"where the user came from this time" use `open_app_from_ev` (see
[`OPEN_APP_FROM_GUIDE.md`](OPEN_APP_FROM_GUIDE.md)) — the two are
complementary.

---

## Common pitfalls

**Forgetting to call `super.onCreate()` before `install`.** Trivial but
catches people: `install` requires the `Application` to be fully constructed.

**Multiple `Application` classes** (rare — usually multi-process tests).
`ProcessLifecycleOwner` is per-process; `install` from each process's
`onCreate` if all of them log this event.

**`FirebasePrefs` shared across modules.** The previous open timestamp is
stored under a stable key (`last_time_open_app`) inside the shared prefs file
`firebase_prefs`. Don't clear that file from arbitrary code paths — only
through `FirebasePrefs.clear(context)` if you need to reset.

---

## Reference

- API: [`TimeOpenAppTracker`](../src/main/java/com/tohsoft/app_event/TimeOpenAppTracker.kt),
  [`AppEventsInstaller`](../src/main/java/com/tohsoft/app_event/AppEventsInstaller.kt).
- Firebase log layer: [`AnalyticsEvents.logTimeOpenAppEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsEvents.kt),
  [`TimeOpenAppEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/models/TimeOpenAppEv.kt).
- Remote-config toggle: `time_open_app_ev_enable` in `EventConfigs`.
