# Integration guide — adding `firebase-events` to a new project

This is the step-by-step for dropping the SDK into a fresh Android project.
Total time: ~15 minutes once Firebase is set up.

> If you are reading this from inside the `toh-weather` repo, the wiring is
> already done — see [`AbsLifeCycleApplication.initAnalytics`](../../app/src/main/java/com/tohsoft/weather/AbsLifeCycleApplication.kt) for the reference call.

---

## Prerequisites

- Android Studio with AGP 8.x, JDK 17.
- A Firebase project. One Firebase Android app per flavor/applicationId if your
  app has multiple flavors. Download each `google-services.json` and place it
  under `app/src/<flavor>/google-services.json`.
- Your `app/build.gradle.kts` already applies `com.google.gms.google-services`
  and `com.google.firebase.crashlytics` plugins.

---

## Step 1 — Copy the module

```bash
cp -R /path/to/firebase-events your-project/firebase-events
```

Folder layout you should see:

```
your-project/
├── app/
├── firebase-events/        ← the SDK
│   ├── build.gradle.kts
│   ├── VERSION
│   ├── README.md
│   ├── docs/
│   └── src/main/java/com/tohsoft/firebase_events/...
└── settings.gradle.kts
```

---

## Step 2 — Wire it into Gradle

In `settings.gradle.kts`:

```kotlin
include(":firebase-events")
```

In `app/build.gradle.kts` dependencies block:

```kotlin
implementation(project(":firebase-events"))
```

If your project uses a version catalog (`libs.versions.toml`), make sure these
catalog entries exist (the SDK's `build.gradle.kts` references them):

```toml
[libraries]
androidx-core-ktx = ...
androidx-appcompat = ...
androidx-lifecycle-viewmodel-ktx = ...
androidx-navigation-fragment-ktx = ...
gson = "com.google.code.gson:gson:2.10.1"
material = ...
firebase-core = ...
firebase-config = ...
firebase-analytics = ...
firebase-crashlytics = ...
```

If you don't use a version catalog, replace `libs.xxx` references in
`firebase-events/build.gradle.kts` with direct `"group:artifact:version"`
strings.

---

## Step 3 — Initialize in Application

```kotlin
class MyApp : Application() {

    private var appOpenedAt: Long = 0L

    override fun onCreate() {
        super.onCreate()

        AnalyticsModule.init(
            appProvider = { this },
            sessionProvider = {
                if (appOpenedAt == 0L) null
                else System.currentTimeMillis() - appOpenedAt
            },
            isTestMode = BuildConfig.DEBUG
        )

        appOpenedAt = System.currentTimeMillis()
    }
}
```

Register `MyApp` in `AndroidManifest.xml`:

```xml
<application android:name=".MyApp" ...>
```

The SDK reuses the saved `EventConfigs` JSON and the saved `TestLogMode` from
SharedPreferences automatically — no extra wiring.

---

## Step 4 — Log your first event

```kotlin
import com.tohsoft.firebase_events.AnalyticsEvents
import com.tohsoft.firebase_events.models._ScreenViewEv
import com.tohsoft.firebase_events.models._ScreenViewEv.State

override fun onPause() {
    super.onPause()
    AnalyticsEvents.logScreenViewEv(
        _ScreenViewEv(
            screenName = "home",
            screenState = State.STOP,
            popupName = "",
            duration = secondsSinceScreenOpened()
        )
    )
}
```

Verify in Firebase DebugView:

```bash
adb shell setprop debug.firebase.analytics.app your.app.id
```

Then open Firebase Console → Analytics → DebugView and watch the events stream in.

---

## Step 5 — (Optional) Wire test-log webhook

The SDK ships with Telegram support out of the box. Webhook support is opt-in
via an adapter you write yourself.

```kotlin
// MyChatWebhook.kt — anywhere in your app module
import com.tohsoft.firebase_events.utils.WebhookSender

object MyChatWebhook : WebhookSender {
    override fun initialize(groupName: String, userName: String, password: String) {
        // Use these to configure your transport (Slack URL, Discord token, etc.)
    }

    override fun sendEvent(message: String) {
        // POST the message to your chosen endpoint
    }
}
```

```kotlin
// In Application.onCreate, BEFORE AnalyticsModule.init
AnalyticsModule.setWebhookSender(MyChatWebhook)
```

Then a tester (or QA Settings screen) calls:

```kotlin
AnalyticsModule.setWebhookInfo(context, "my_group", "username", "secret")
```

…and from that point on, every event the SDK would have sent to Firebase is
forwarded as text to your webhook instead (when `TestLogMode.WEBHOOK` is active).

If you don't call `setWebhookSender`, `TestLogMode.WEBHOOK` silently drops
messages — Telegram mode still works.

---

## Step 6 — (Optional) Plug your project event catalog

For project-specific events (screen names, button names, dialog names), see
[`PROJECT_EVENT_TEMPLATE.md`](PROJECT_EVENT_TEMPLATE.md). The pattern is:

1. Define a `screen_*` / `btn_*` constants file in your app module.
2. Write a thin wrapper (mirror of [`AnalyticsEventsUtils.kt`](../../app/src/main/java/com/tohsoft/weather/utils/extensions/AnalyticsEventsUtils.kt) in the reference project).
3. Call the wrapper from your UI code; the wrapper calls the SDK.

---

## Common pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| No events show up in DebugView | Forgot `adb shell setprop debug.firebase.analytics.app <appId>` | Set it; restart app |
| `WebhookApi` unresolved during build | Project doesn't have `:webhook` module | Either inject a custom `WebhookSender` (see Step 5) or remove the adapter file from your project |
| Build fails on `libs.firebase.crashlytics` | Missing version catalog entry | Add it to `libs.versions.toml` or hardcode the coordinate |
| `AnalyticsModule.getApplication()` returns null | `init` was never called | Call `init` from `Application.onCreate` BEFORE the first activity is created |
| Events show in DebugView but not in the standard reports | Less than ~24h wait, or you violated a 40-char limit | Check the soft warnings in Logcat (`AnalyticsValidator` tag) |
