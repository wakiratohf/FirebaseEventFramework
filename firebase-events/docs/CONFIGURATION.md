# Configuration

How to control what the SDK does at runtime — toggles, consent, and test logging.

---

## `EventConfigs` — per-event-type toggle

Every built-in event has an `isEnabled` flag in [`EventConfigs.kt`](../src/main/java/com/tohsoft/firebase_events/models/EventConfigs.kt). The SDK reads from a `EventConfigs` instance held by `AnalyticsEvents`; if a flag is false, the corresponding event is dropped.

Why care: you can ramp down a noisy event in production without shipping an app update, by pushing a new config via Firebase Remote Config.

### Schema

```json
{
  "iap_ev_enable": true,
  "show_rate_dialog_ev_enable": true,
  "time_open_app_ev_enable": true,
  "load_ad_ev_enable": true,
  "show_ad_ev_enable": true,
  "click_ad_ev_enable": true,
  "open_app_from_ev_enable": true,
  "screen_view_ev_enable": true,
  "click_btn_ev_enable": true,
  "paid_ad_impression_ev_enable": true,
  "app_exit_ev_enable": true,
  "onboarding_step_enable": true
}
```

### Apply from Remote Config

```kotlin
val remoteJson = FirebaseRemoteConfig.getInstance().getString("event_configs")
val cfg = Gson().fromJson(remoteJson, EventConfigs::class.java)
AnalyticsModule.setEventConfigs(context, cfg)  // persists to prefs + applies in memory
```

The SDK auto-restores the persisted JSON on next `AnalyticsModule.init`.

---

## Master kill-switch (`setEnabled`)

`AnalyticsModule.isEnabled` is a single bool that short-circuits every log
call. Use to honour user consent (GDPR / CCPA / Korea PIPA).

```kotlin
// User declined consent
AnalyticsModule.setEnabled(false)

// User accepted later
AnalyticsModule.setEnabled(true)
```

Default is `true`. The value is NOT persisted by the SDK — store the user's
choice in your own settings store and re-apply on every `init`.

> The kill-switch does not unwind already-logged events; it only prevents new
> ones from being sent.

---

## Test-log mode

When you're hunting an analytics bug, you don't want to wait for Firebase
DebugView to refresh or BigQuery to ingest. The SDK can mirror every event to
a side channel instead.

### Modes ([`TestLogMode`](../src/main/java/com/tohsoft/firebase_events/AnalyticsModule.kt))

| Mode | Behaviour |
|---|---|
| `NONE` | Events go to Firebase only. |
| `TELEGRAM` | Events are batched (5 at a time) and posted to a Telegram bot chat. Built-in. |
| `WEBHOOK` | Events are forwarded as text to a [`WebhookSender`](../src/main/java/com/tohsoft/firebase_events/utils/WebhookSender.kt) you register. No-op if none is registered. |

### Configure Telegram

```kotlin
AnalyticsModule.setBotInfo(context, botToken = "...", chatId = "...")
// Mode automatically switches to TELEGRAM and persists.
```

`TelegramBot` uses a raw `HttpURLConnection` POST — no third-party deps.

### Configure webhook

```kotlin
// 1. Implement WebhookSender once
object MyWebhook : WebhookSender { /* ... */ }

// 2. Register, ideally in Application.onCreate before AnalyticsModule.init
AnalyticsModule.setWebhookSender(MyWebhook)

// 3. From a settings/QA screen, push credentials
AnalyticsModule.setWebhookInfo(context, "group", "user", "secret")
// Mode automatically switches to WEBHOOK and persists.
```

The SDK persists `groupName/userName/password` in prefs so the next launch
auto-restores them.

> **Note on call order:** `setWebhookSender` only attaches the sender — it
> does NOT call `sender.initialize(...)`. The actual initialization happens
> either in `AnalyticsModule.init` (using persisted credentials) or in
> `setWebhookInfo` (using fresh credentials). Calling `setWebhookSender`
> repeatedly with the same instance is a cheap no-op, so it's safe to call
> from both `Application.onCreate` and `MainActivity.onCreate` if the app
> re-initializes analytics on a per-session basis.

### Switch mode programmatically

```kotlin
AnalyticsModule.setLogMode(TestLogMode.WEBHOOK)
AnalyticsModule.setLogMode(TestLogMode.NONE)
```

---

## Soft naming validation

When `AnalyticsModule.isTestMode == true`, every `logEvent` call passes through
[`EventNameValidator`](../src/main/java/com/tohsoft/firebase_events/utils/EventNameValidator.kt) — a non-throwing checker for Firebase's hard limits:

- Event name ≤ 40 chars, matches `[A-Za-z][A-Za-z0-9_]*`
- Param key ≤ 40 chars
- Param string value ≤ 100 chars

Violations are logged to Logcat with tag `AnalyticsValidator`. Production
builds (where `isTestMode == false`) skip validation entirely.

---

## Recommended flavor wiring

Most projects want analytics on in `release`, off in `debug`. The SDK already
takes an `isTestMode: Boolean` at `init`:

```kotlin
AnalyticsModule.init(
    appProvider = { this },
    sessionProvider = { sessionMs() },
    isTestMode = BuildConfig.DEBUG || BuildConfig.IS_INTERNAL_TESTING
)
```

`isTestMode == true` does NOT disable the SDK — it just enables the validator
and changes how `logEvent` behaves (it formats a human-readable dump to
Logcat and to the test-log transport).

If you want to fully disable analytics in a flavor, use the master kill-switch:

```kotlin
if (BuildConfig.FLAVOR == "internal_qa") {
    AnalyticsModule.setEnabled(false)
}
```
