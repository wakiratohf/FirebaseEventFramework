# Sample integration snippet

A minimal, copy-pasteable end-to-end example showing how a fresh Android app
wires up the SDK. Read this *after* [`INTEGRATION.md`](INTEGRATION.md) — that
doc covers Gradle setup; this doc shows the Kotlin code on top of it.

> The reference project (`toh-weather`) is a 50-screen production app, so its
> wiring is harder to read. This file is the "hello world" version.

---

## What this sample demonstrates

- Initialise the SDK once in `Application.onCreate`
- Track a session (foreground / background) without a 3rd-party library
- Log a screen view, a button click, an in-app purchase
- Set a user property
- Honour GDPR consent via `setEnabled`
- Plug a (mocked) webhook transport for QA
- Define a project-specific event via the `AnalyticsEvent` interface

---

## File 1 — `MyApp.kt`

```kotlin
package com.example.myapp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.tohsoft.firebase_events.AnalyticsModule
import com.tohsoft.firebase_events.AnalyticsUserProperties

class MyApp : Application(), Application.ActivityLifecycleCallbacks {

    private var foregroundedAt: Long = 0L

    override fun onCreate() {
        super.onCreate()

        // 1. (Optional) Register a webhook transport BEFORE init so it can
        //    auto-apply saved credentials from prefs.
        AnalyticsModule.setWebhookSender(MySlackWebhookSender)

        // 2. Initialise the SDK.
        AnalyticsModule.init(
            appProvider = { this },
            sessionProvider = { sessionDurationMs() },
            isTestMode = BuildConfig.DEBUG
        )

        // 3. (Optional) Honour previous consent decision.
        val consented = getSharedPreferences("consent", MODE_PRIVATE)
            .getBoolean("analytics_consent", true)
        AnalyticsModule.setEnabled(consented)

        // 4. Push one-time user properties.
        AnalyticsUserProperties.logLanguageAndAppVersion(
            language = resources.configuration.locales[0].language,
            appVersion = BuildConfig.VERSION_NAME
        )

        // 5. Listen for foreground/background to populate sessionProvider.
        registerActivityLifecycleCallbacks(this)
    }

    private fun sessionDurationMs(): Long? =
        if (foregroundedAt == 0L) null else System.currentTimeMillis() - foregroundedAt

    override fun onActivityResumed(activity: Activity) {
        if (foregroundedAt == 0L) foregroundedAt = System.currentTimeMillis()
    }

    override fun onActivityStopped(activity: Activity) {
        // Naive: assume "stopped" means backgrounded. Real apps use
        // ProcessLifecycleOwner — see the reference project for that pattern.
        foregroundedAt = 0L
    }

    // Other ActivityLifecycleCallbacks methods omitted for brevity.
    override fun onActivityCreated(a: Activity, s: Bundle?) {}
    override fun onActivityStarted(a: Activity) {}
    override fun onActivityPaused(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    override fun onActivityDestroyed(a: Activity) {}
}
```

---

## File 2 — `MySlackWebhookSender.kt`

Optional. Only needed if QA wants to mirror events to Slack/Discord/etc.
during testing. Implementation is intentionally fake — replace the body with
your real transport (OkHttp POST, gRPC, etc.).

```kotlin
package com.example.myapp

import com.tohsoft.firebase_events.utils.WebhookSender

object MySlackWebhookSender : WebhookSender {

    private var webhookUrl: String = ""
    private var channel: String = ""

    override fun initialize(groupName: String, userName: String, password: String) {
        // Map the SDK's generic params onto Slack's notion:
        //   groupName -> webhook URL
        //   userName  -> channel
        //   password  -> bot token (ignored in this stub)
        webhookUrl = groupName
        channel = userName
    }

    override fun sendEvent(message: String) {
        if (webhookUrl.isEmpty()) return
        // POST { "channel": channel, "text": message } to webhookUrl
        // (omitted — use OkHttp/HttpURLConnection here)
    }
}
```

---

## File 3 — Logging from an Activity

```kotlin
package com.example.myapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tohsoft.firebase_events.AnalyticsEvents
import com.tohsoft.firebase_events.AnalyticsUserProperties
import com.tohsoft.firebase_events.models.IAPEv
import com.tohsoft.firebase_events.models._ClickBtnEv
import com.tohsoft.firebase_events.models._ScreenViewEv

class HomeActivity : AppCompatActivity() {

    private var screenOpenedAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        screenOpenedAt = System.currentTimeMillis()
        AnalyticsUserProperties.logEventScreenOpen("home")

        findViewById<View>(R.id.btn_refresh).setOnClickListener {
            AnalyticsEvents.logClickBtnEv(
                _ClickBtnEv(
                    screenName = "home",
                    buttonName = "btn_refresh",
                    popupName = "",
                    time = secondsSinceAppOpen()
                )
            )
            // ... do refresh ...
        }

        findViewById<View>(R.id.btn_buy_pro).setOnClickListener {
            startInAppPurchase(productId = "premium_yearly")
        }
    }

    override fun onStop() {
        super.onStop()
        val duration = ((System.currentTimeMillis() - screenOpenedAt) / 1000).toInt()
        AnalyticsEvents.logScreenViewEv(
            _ScreenViewEv(
                screenName = "home",
                screenState = _ScreenViewEv.State.STOP,
                popupName = "",
                duration = duration
            )
        )
    }

    private fun secondsSinceAppOpen(): Int {
        val ts = com.tohsoft.firebase_events.AnalyticsModule.getAppOpenedTimestamp() ?: return 0
        return ((System.currentTimeMillis() - ts) / 1000).toInt()
    }

    private fun startInAppPurchase(productId: String) {
        // ... Play Billing flow ...
        val paymentOk = true
        val isTrial = false
        AnalyticsEvents.logIAPEv(
            IAPEv(
                where = "home_pro_icon",
                paymentSuccess = paymentOk,
                isTrial = isTrial,
                productId = productId
            )
        )
    }
}
```

---

## File 4 — Defining a project-specific event

When the built-in 13 events don't cover your need, don't fork the SDK.
Implement [`AnalyticsEvent`](../src/main/java/com/tohsoft/firebase_events/models/AnalyticsEvent.kt)
and call `AnalyticsEvents.logEvent(event)`.

```kotlin
package com.example.myapp.events

import android.os.Bundle
import com.tohsoft.firebase_events.models.AnalyticsEvent

/** Logged when the user pulls down to refresh content. */
data class PullToRefreshEv(
    val screenName: String,
    val itemsLoaded: Int,
    val durationMs: Long
) : AnalyticsEvent {

    override val eventName: String = "pull_to_refresh"

    override fun toBundle(): Bundle = Bundle().apply {
        putString("screen_name", screenName)
        putInt("items_loaded", itemsLoaded)
        putLong("duration_ms", durationMs)
    }
}

// Call site
AnalyticsEvents.logEvent(
    PullToRefreshEv(
        screenName = "home",
        itemsLoaded = 24,
        durationMs = 432
    )
)
```

---

## File 5 — Toggling consent at runtime

```kotlin
package com.example.myapp

import com.tohsoft.firebase_events.AnalyticsModule

class ConsentDialog {

    fun onUserAccepted() {
        AnalyticsModule.setEnabled(true)
        persistConsent(true)
    }

    fun onUserDeclined() {
        AnalyticsModule.setEnabled(false)
        persistConsent(false)
    }

    private fun persistConsent(accepted: Boolean) {
        // Your own SharedPreferences/DataStore call here.
    }
}
```

---

## What this sample skips

| Topic | Where to read about it |
|---|---|
| Automating screen-view tracking via a `BaseFragment` | [`PROJECT_EVENT_TEMPLATE.md`](PROJECT_EVENT_TEMPLATE.md), section "Automate screen-view tracking" |
| Wiring up Firebase Remote Config to push `EventConfigs` JSON | [`CONFIGURATION.md`](CONFIGURATION.md), section "Apply from Remote Config" |
| Telegram bot setup | [`CONFIGURATION.md`](CONFIGURATION.md), section "Configure Telegram" |
| Wiring up Crashlytics so logger failures get reported | The SDK already calls `FirebaseCrashlytics.getInstance().recordException(...)` for caught errors. Just ensure Crashlytics is initialised in your app. |
| Bottom-sheet / dialog screen tracking | Pass the dialog's name as `popupName` on `_ScreenViewEv` / `_ClickBtnEv` |
