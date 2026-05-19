# Integration guide — adding `firebase-events` to a new project

This is the step-by-step for dropping the SDK into a fresh Android project.
Total time: ~15 minutes once Firebase is set up.

> **Module ordering:** this guide wires the **base SDK** (`:firebase-events`).
> If your project also needs the lifecycle helpers (`open_app_from_ev`,
> `time_open_app_ev`, `app_exit`), integrate `:firebase-events` first
> (this guide), then follow
> [`app-event/docs/INTEGRATION.md`](../../app-event/docs/INTEGRATION.md).
> The reverse order does not work — `:app-event` depends on this module.

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

## Step 1 — Copy the modules

Copy **both** `firebase-events/` and `firebase-events-lint/`:

```bash
cp -R /path/to/firebase-events       your-project/firebase-events
cp -R /path/to/firebase-events-lint  your-project/firebase-events-lint
```

Folder layout you should see:

```
your-project/
├── app/
├── firebase-events/             ← the SDK
│   ├── build.gradle.kts
│   ├── VERSION
│   ├── README.md
│   ├── docs/
│   └── src/main/java/com/tohsoft/firebase_events/...
├── firebase-events-lint/        ← Lint rules enforcing buttonName convention
│   ├── build.gradle.kts
│   ├── gradle.properties        ← required: disables auto kotlin-stdlib
│   └── src/main/java/com/tohsoft/firebase_events/lint/...
└── settings.gradle.kts
```

---

## Step 2 — Wire it into Gradle

In `settings.gradle.kts`:

```kotlin
include(":firebase-events")
include(":firebase-events-lint")
```

In root `build.gradle.kts` add the two extra plugin aliases (`apply false`):

```kotlin
plugins {
    // ... existing aliases ...
    alias(libs.plugins.kotlinJvm) apply false      // for :firebase-events-lint
    alias(libs.plugins.androidLint) apply false    // for :firebase-events-lint
}
```

In `app/build.gradle.kts` dependencies block:

```kotlin
implementation(project(":firebase-events"))
lintChecks(project(":firebase-events-lint"))
```

And promote Lint issues to ERROR (so they fail the build):

```kotlin
android {
    lint {
        error += setOf(
            "ClickBtnEvUnderscore",
            "ClickBtnEvBtnPrefix",
            "ClickBtnEvNotCamelCase",
            "ClickBtnEvEmpty",
        )
    }
}
```

If your project uses a version catalog (`libs.versions.toml`), make sure these
catalog entries exist (the SDK's `build.gradle.kts` and the Lint module's
`build.gradle.kts` reference them):

```toml
[versions]
# Lint API: AGP_major + 23. AGP 8.6 → 31.6; AGP 9.2 → 32.2.
lintApi = "31.6.0"

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
lint-api = { group = "com.android.tools.lint", name = "lint-api", version.ref = "lintApi" }
lint-checks = { group = "com.android.tools.lint", name = "lint-checks", version.ref = "lintApi" }
lint-tests = { group = "com.android.tools.lint", name = "lint-tests", version.ref = "lintApi" }

[plugins]
kotlinJvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
androidLint = { id = "com.android.lint", version.ref = "agp" }
```

If you don't use a version catalog, replace `libs.xxx` references in
`firebase-events/build.gradle.kts` and `firebase-events-lint/build.gradle.kts`
with direct `"group:artifact:version"` strings.

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

## Step 7 — (Optional) Add `:app-event` for lifecycle events

`:firebase-events` ships the formatters for `time_open_app_ev`,
`app_exit`, and `open_app_from_ev` but **not** the Android lifecycle
plumbing that fires them. To enable those events end-to-end, integrate
the companion `:app-event` module after finishing the steps above —
follow [`app-event/docs/INTEGRATION.md`](../../app-event/docs/INTEGRATION.md).

The module provides a one-line installer
(`AppEventsInstaller.install(application)`) plus explicit-call APIs
for projects with custom lifecycle requirements.

---

## Common pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| No events show up in DebugView | Forgot `adb shell setprop debug.firebase.analytics.app <appId>` | Set it; restart app |
| `WebhookApi` unresolved during build | Project doesn't have `:webhook` module | Either inject a custom `WebhookSender` (see Step 5) or remove the adapter file from your project |
| Build fails on `libs.firebase.crashlytics` | Missing version catalog entry | Add it to `libs.versions.toml` or hardcode the coordinate |
| `AnalyticsModule.getApplication()` returns null | `init` was never called | Call `init` from `Application.onCreate` BEFORE the first activity is created |
| Events show in DebugView but not in the standard reports | Less than ~24h wait, or you violated a 40-char limit | Check the soft warnings in Logcat (`AnalyticsValidator` tag) |
| `:firebase-events-lint:assemble` → *"plugin already on the classpath with an unknown version"* | Root `build.gradle.kts` is missing `alias(libs.plugins.kotlinJvm) apply false` and `alias(libs.plugins.androidLint) apply false` | Add both aliases to the root plugins block |
| `:firebase-events:prepareLintJarForPublish` → *"Found more than one jar in the lintPublish configuration"* | `:firebase-events-lint` is publishing transitive `kotlin-stdlib` | Make sure `firebase-events-lint/gradle.properties` contains `kotlin.stdlib.default.dependency=false` and stdlib is declared `compileOnly` |
| Lint runs but `ClickBtnEv*` issues never fire | No enum in `:app` implements an interface literally named `ClickBtnEv` | Create `event/ClickBtnEv.kt` marker (3 props: `screenName`, `buttonName`, `popupName`) and make screen enums implement it |
