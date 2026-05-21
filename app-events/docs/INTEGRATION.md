# Integration guide — adding `:app-event` to a project

This is the wiring for the lifecycle helpers (`time_open_app_ev`,
`app_exit`, `open_app_from_ev`) on top of `:firebase-events`. Time:
~5 minutes once `:firebase-events` is already wired.

> Quick orientation for this module's docs:
> - **Why this module exists** → [`../README.md`](../README.md).
> - **Wiring (you are here)** → this file.
> - **Per-event recipes** → [`OPEN_APP_FROM_GUIDE.md`](OPEN_APP_FROM_GUIDE.md),
>   [`TIME_OPEN_APP_GUIDE.md`](TIME_OPEN_APP_GUIDE.md), [`APP_EXIT_GUIDE.md`](APP_EXIT_GUIDE.md),
>   [`RATE_DIALOG_GUIDE.md`](RATE_DIALOG_GUIDE.md).
> - **Screen tracking** (`screen_view_ev` + `click_btn_ev`, implemented in
>   `:app` — not this module) → [`SCREEN_VIEW_GUIDE.md`](SCREEN_VIEW_GUIDE.md).
>   Not wired by this guide; see that playbook if you also need it.

---

## Prerequisites

`:firebase-events` MUST be integrated first — `:app-event` calls
`AnalyticsEvents.logTimeOpenAppEv` / `logAppExitEv` / `logOpenAppFromEv`
and reads `FirebasePrefs` shared state. Complete every step of
[`../../firebase-events/docs/INTEGRATION.md`](../../firebase-events/docs/INTEGRATION.md)
before starting here, and verify the SDK logs at least one event into
Firebase DebugView.

Why the order matters:

- `app-event/build.gradle.kts` declares
  `implementation(project(":firebase-events"))`. Gradle won't sync
  otherwise.
- `AppExitTracker` reads `FirebasePrefs.getAppOpenedTimestamp` —
  shared prefs wired in the base SDK module.
- Without `AnalyticsModule.init(...)`, every log call early-returns
  (`AnalyticsModule.isEnabled == false`), so trackers appear silent
  even though they fire.

---

## Step 1 — Copy the module

```bash
cp -R /path/to/app-event your-project/app-event
```

Folder layout you should see:

```
your-project/
├── app/
├── firebase-events/          ← already there from previous guide
├── firebase-events-lint/     ← already there
├── app-event/                ← NEW
│   ├── build.gradle.kts
│   ├── consumer-rules.pro
│   ├── VERSION
│   ├── README.md
│   ├── .gitignore
│   ├── docs/
│   └── src/main/java/com/tohsoft/app_event/...
└── settings.gradle.kts
```

---

## Step 2 — Wire it into Gradle

In `settings.gradle.kts`:

```kotlin
include(":firebase-events")        // already there
include(":firebase-events-lint")   // already there
include(":app-event")              // NEW
```

In `gradle/libs.versions.toml`, add the one extra catalog entry the
module pulls (`androidx.core.ktx`, `kotlinx.coroutines.android`,
`junit` are reused from the `:firebase-events` setup):

```toml
[versions]
lifecycleProcess = "2.10.0"

[libraries]
androidx-lifecycle-process = { group = "androidx.lifecycle", name = "lifecycle-process", version.ref = "lifecycleProcess" }
```

In `app/build.gradle.kts` dependencies block:

```kotlin
dependencies {
    implementation(project(":firebase-events"))   // already there
    implementation(project(":app-event"))         // NEW
    lintChecks(project(":firebase-events-lint"))  // already there
}
```

Sync Gradle. All three modules should resolve green.

---

## Step 3 — Persist `appOpenedTimestamp`

`AppExitTracker.onAppExit` computes `duration_sec` as
`now - FirebasePrefs.getAppOpenedTimestamp(context)`. The host app
marks session start — the module doesn't guess where:

```kotlin
import com.tohsoft.firebase_events.AnalyticsModule
import com.tohsoft.firebase_events.utils.FirebasePrefs
import com.tohsoft.app_event.AppEventsInstaller

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        AnalyticsModule.init(/* … from :firebase-events guide … */)
        FirebasePrefs.saveAppOpenedTimestamp(this, System.currentTimeMillis())

        AppEventsInstaller.install(this)   // Step 4 — Pattern A
    }
}
```

Without `saveAppOpenedTimestamp`, every `app_exit` log is suppressed
(`timestamp == 0L` short-circuits). Pick a moment that matches your
definition of "session start" — most apps use `Application.onCreate`
or the first `MainActivity.onCreate`.

---

## Step 4 — Pick an integration pattern

For the **two lifecycle events** (`time_open_app_ev`, `app_exit`),
choose ONE of the four patterns below:

| Pattern | When to use |
|---|---|
| **A. `AppEventsInstaller.install(this)`** | New projects, no special lifecycle requirements — 1 line, drop-in. |
| **B. `ProcessLifecycleOwner` observer + tracker calls** | You already maintain a process-lifecycle observer or need explicit conditions. |
| **C. `Application.ActivityLifecycleCallbacks` + counters** | You prefer not to pull `lifecycle-process` into your own code. |
| **D. Hybrid (production reference: `toh-weather`)** | You need extra triggers besides `ON_START` / `ON_STOP` — e.g. a full-screen overlay dismiss. |

Detailed code for each pattern lives in the per-event guides:

- [`TIME_OPEN_APP_GUIDE.md`](TIME_OPEN_APP_GUIDE.md) — Patterns A/B/C/D for `time_open_app_ev`.
- [`APP_EXIT_GUIDE.md`](APP_EXIT_GUIDE.md) — Patterns A/B/C/D for `app_exit`.

> ⚠ Do **not** combine Pattern A with explicit tracker calls (Patterns
> B/C/D). The installer already fires `onSessionStart` / `onAppExit`
> via `ProcessLifecycleOwner`; an extra trigger would log the event
> twice per session.

The other two events are **call-based** (no installer): fire them from your
own code where the event happens.

- `open_app_from_ev` — each app's launch sources differ; wire your intent
  producers + launcher Activity per [`OPEN_APP_FROM_GUIDE.md`](OPEN_APP_FROM_GUIDE.md).
- `show_rate_dialog_ev` — call `RateDialogEventTracker.logShowRateDialog(...)`
  from each rate-dialog button handler per
  [`RATE_DIALOG_GUIDE.md`](RATE_DIALOG_GUIDE.md).

---

## Step 5 — (Optional) Map activities to screen names

`AppExitTracker.setLastActiveScreen(...)` records the screen the user
was on when the app went to background. The installer uses each
Activity's `simpleName` by default. To skip splash/ad activities or
substitute domain names:

```kotlin
AppEventsInstaller.install(this) { activity ->
    when (activity) {
        is SplashActivity, is AdActivity -> null      // keep previous screen
        is MainActivity -> "home"                     // domain-level name
        else -> activity.javaClass.simpleName         // fallback
    }
}
```

Returning `null` preserves the previously-set screen — useful for
brief overlays you don't want to attribute the exit to. You can also
call `AppExitTracker.setLastActiveScreen(name)` from your own screen
helper; the last call wins.

> 💡 If your app also implements screen tracking
> ([`SCREEN_VIEW_GUIDE.md`](SCREEN_VIEW_GUIDE.md)), reuse its `ScreenName`
> catalog here instead of hard-coding strings — one source of truth for
> screen names keeps `last_active_screen` consistent with `screen_view_ev`.

---

## Step 6 — Verify

1. Enable Firebase DebugView (same `adb shell setprop` from the
   `:firebase-events` guide):

   ```bash
   adb shell setprop debug.firebase.analytics.app your.app.id
   ```

2. Foreground the app → expect `time_open_app_ev` shortly after the
   first `ON_START`. First session shows `delta=""` and
   `last_in_hour=""` — that's correct.
3. Background the app (press Home) → expect `app_exit` within ~1s of
   `ON_STOP` (700ms grace + 1s debounce).
4. If you wired `open_app_from_ev`, tap a tagged notification /
   shortcut → expect `OAF_ev_{source}_{hour}`.

In test mode (`isTestMode = true` passed to `AnalyticsModule.init`),
events are dumped to Logcat with tag `AnalyticsEvents` instead of
being sent to Firebase — handy for offline verification.

---

## Common pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| `time_open_app_ev` shows `delta=""` every session | App writes `appOpenedTimestamp` AFTER `onSessionStart` runs | Save the timestamp in `Application.onCreate` BEFORE calling `AppEventsInstaller.install(...)` |
| `app_exit` never logs | `appOpenedTimestamp == 0` OR `lastActiveScreen` empty | Call `FirebasePrefs.saveAppOpenedTimestamp` (Step 3); make sure the user reaches a real screen before backgrounding |
| Two `app_exit` per session | Installer + explicit `onAppExit` both active | Pick ONE pattern (see warning in Step 4) |
| `last_active_screen` shows splash/ad activity | Default mapping uses `simpleName` of every Activity | Pass a screen mapper to `install { activity -> … }` — return `null` to skip (Step 5) |
| `open_app_from_ev` only fires on cold start | `singleTop` / `singleTask` launcher Activity needs `onNewIntent` wiring | Call `OpenAppFromIntent.logFromIntent(intent, …)` in both `onCreate` AND `onNewIntent` |
| Gradle sync error: unresolved `androidx-lifecycle-process` | Missing catalog entry | Add the `lifecycleProcess` version + library alias from Step 2 |
| Events fire but never reach Firebase | `AnalyticsModule.isEnabled == false` or `isTestMode == true` | Verify the `init` call in the base SDK guide; toggle `AnalyticsModule.setEnabled(true)` if you persist consent |

---

## Reference

- Module API: [`src/main/java/com/tohsoft/app_event/`](../src/main/java/com/tohsoft/app_event/) —
  [`AppEventsInstaller`](../src/main/java/com/tohsoft/app_event/AppEventsInstaller.kt),
  [`AppExitTracker`](../src/main/java/com/tohsoft/app_event/AppExitTracker.kt),
  [`TimeOpenAppTracker`](../src/main/java/com/tohsoft/app_event/TimeOpenAppTracker.kt),
  [`OpenAppFromIntent`](../src/main/java/com/tohsoft/app_event/OpenAppFromIntent.kt),
  [`OpenAppSource`](../src/main/java/com/tohsoft/app_event/OpenAppSource.kt),
  [`RateDialogEventTracker`](../src/main/java/com/tohsoft/app_event/RateDialogEventTracker.kt).
- Per-event guides: [`OPEN_APP_FROM_GUIDE.md`](OPEN_APP_FROM_GUIDE.md),
  [`TIME_OPEN_APP_GUIDE.md`](TIME_OPEN_APP_GUIDE.md),
  [`APP_EXIT_GUIDE.md`](APP_EXIT_GUIDE.md),
  [`RATE_DIALOG_GUIDE.md`](RATE_DIALOG_GUIDE.md).
- Screen tracking playbook (lives in `:app`):
  [`SCREEN_VIEW_GUIDE.md`](SCREEN_VIEW_GUIDE.md).
- Underlying log layer (`:firebase-events`):
  [`AnalyticsEvents`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsEvents.kt),
  schema in [`EVENT_CATALOG.md`](../../firebase-events/docs/EVENT_CATALOG.md).
- Remote-config toggles per event: `time_open_app_ev_enable`,
  `app_exit_ev_enable`, `open_app_from_ev_enable`,
  `show_rate_dialog_ev_enable` in
  [`CONFIGURATION.md`](../../firebase-events/docs/CONFIGURATION.md).
