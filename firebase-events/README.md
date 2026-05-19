# firebase-events

A drop-in Firebase Analytics + Crashlytics event-tracking layer for Android apps.

**Version:** see [`VERSION`](VERSION) · **License:** Apache 2.0 · **minSdk 21 · compileSdk 35 · JDK 17**

This module is designed to be **copy-pasted** into any Android project. It has
zero hard dependencies on other in-house modules — the only required runtime
deps are Firebase Analytics, Firebase Crashlytics, Firebase Config, and Gson.

---

## What it gives you

| Capability | Where |
|---|---|
| 13 ready-made event types (screen view, click, ads, IAP, lifecycle, ...) | [`docs/EVENT_CATALOG.md`](docs/EVENT_CATALOG.md) |
| 9 ready-made user properties (language, country, tier, permission, ...) | [`docs/EVENT_CATALOG.md`](docs/EVENT_CATALOG.md) |
| `AnalyticsEvent` interface for project-defined events | [`docs/PROJECT_EVENT_TEMPLATE.md`](docs/PROJECT_EVENT_TEMPLATE.md) |
| Server-toggleable event configs (Remote Config friendly) | [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md) |
| Test-log modes (Telegram built-in, custom Webhook via interface) | [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md) |
| GDPR/CCPA master kill-switch (`setEnabled(false)`) | [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md) |
| Soft validation of Firebase naming limits (debug mode) | `utils/EventNameValidator.kt` |

## What it does NOT give you

| Out of scope | Where it should live |
|---|---|
| Your app's screen-name / button-name catalog | In `:app` — see [`docs/PROJECT_EVENT_TEMPLATE.md`](docs/PROJECT_EVENT_TEMPLATE.md) |
| Mapping domain models → bundle params | In `:app` (you know your data) |
| `google-services.json` | In `:app` per flavor |
| A built-in webhook transport (Slack/Discord/Chatango/etc.) | Inject via [`WebhookSender`](src/main/java/com/tohsoft/firebase_events/utils/WebhookSender.kt) |
| AdRevenue / AdResult concrete classes | Adapt via [`AdRevenueLike`](src/main/java/com/tohsoft/firebase_events/models/AdRevenueLike.kt) |

---

## Quick start (copy-paste install)

```bash
# 1. Copy BOTH module folders into the new project (sibling of :app)
cp -R /path/to/this/firebase-events       your-project/
cp -R /path/to/this/firebase-events-lint  your-project/   # ← Lint rules

# 2. Register both in settings.gradle.kts
echo 'include(":firebase-events")'      >> your-project/settings.gradle.kts
echo 'include(":firebase-events-lint")' >> your-project/settings.gradle.kts

# 3. In app/build.gradle.kts:
#    implementation(project(":firebase-events"))
#    lintChecks(project(":firebase-events-lint"))
#
#    android { lint { error += setOf(
#        "ClickBtnEvUnderscore", "ClickBtnEvBtnPrefix",
#        "ClickBtnEvNotCamelCase", "ClickBtnEvEmpty",
#    ) } }
```

```kotlin
// 4. Initialize once, in Application.onCreate
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AnalyticsModule.init(
            appProvider = { this },
            sessionProvider = { sessionStartTimestamp() },
            isTestMode = BuildConfig.DEBUG
        )
    }
}
```

```kotlin
// 5. Log events from anywhere
AnalyticsEvents.logClickBtnEv(
    _ClickBtnEv(
        screenName = "home",
        buttonName = "btn_refresh",
        popupName = "",
        time = secondsSinceAppOpen()
    )
)
```

Full step-by-step (Firebase project, flavor config, optional webhook):
→ [`docs/INTEGRATION.md`](docs/INTEGRATION.md)

---

## Architecture in 30 seconds

```
┌─ AnalyticsModule  ── init/config, master kill-switch, transport injection
├─ AnalyticsEvents  ── 13 typed log methods + generic logEvent
├─ AnalyticsUserProperties  ── 9 user-property setters
└─ models/  ── data classes for each event (screen view, click, ads, IAP, ...)
```

Three logical layers (no separate Gradle modules — single module by design):

1. **Core** — `AnalyticsModule`, `FirebasePrefs`, `EventConfigs`, `TelegramBot`, `WebhookSender` interface, `DateTimeHelper`, `EventNameValidator`.
2. **Common events** — `_ScreenViewEv`, `_ClickBtnEv`, `_OpenAppFromEv`, `_ShowRateDialogEv`, `TimeOpenAppEv`, `AppExitEv`, `OnboardingStepEv`, `LoadAdEv`, `ShowAdEv`, `ClickAdEv`, `PaidAdImpressionEv`, `IAPEv`.
3. **User properties** — language, country, app_version, user_tier, subscription_status, has_seen_tutorial, ad_engagement_level, allow_permission, screen_open.

Don't put project-specific screen names or button names inside this module.
They belong in your app — see [`docs/PROJECT_EVENT_TEMPLATE.md`](docs/PROJECT_EVENT_TEMPLATE.md).

---

## Documentation index

| File | Purpose |
|---|---|
| [`docs/CONTEXT.md`](docs/CONTEXT.md) | Domain language: what each term means, glossary |
| [`docs/NAMING_CONVENTION.md`](docs/NAMING_CONVENTION.md) | **Chốt convention** cho `screenName`, `popupName`, `buttonName` — dùng chung cho mọi project copy module này. Lint module `:firebase-events-lint` (sibling, copy kèm) enforce ở compile time với 4 issue ID. |
| [`docs/INTEGRATION.md`](docs/INTEGRATION.md) | Step-by-step setup for a fresh project |
| [`docs/samples/SAMPLE_APP.md`](docs/samples/SAMPLE_APP.md) | End-to-end "hello world" Kotlin code (Application + Activity + custom event + consent) |
| [`docs/samples/DEMO_IMPLEMENTATION_GUIDE.md`](docs/samples/DEMO_IMPLEMENTATION_GUIDE.md) | Wiring guide cho demo project khi Gradle đã sẵn sàng |
| [`docs/samples/IMPLEMENT_PROMPT.md`](docs/samples/IMPLEMENT_PROMPT.md) | Prompt template để agent triển khai SDK trong demo project |
| [`docs/samples/FRESH_IMPLEMENT_PROMPT.md`](docs/samples/FRESH_IMPLEMENT_PROMPT.md) | Prompt template để agent tích hợp SDK vào project từ con số 0 |
| [`docs/EVENT_CATALOG.md`](docs/EVENT_CATALOG.md) | Every built-in event & user property, with bundle schemas |
| [`docs/PROJECT_EVENT_TEMPLATE.md`](docs/PROJECT_EVENT_TEMPLATE.md) | How to add your project's own events without forking the SDK |
| [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md) | EventConfigs toggle, Remote Config, TestLogMode, consent |
| [`docs/MIGRATION.md`](docs/MIGRATION.md) | Migration notes for each version bump |
| [`VERSION`](VERSION) | Current version (semver). Bump it when you change the public API |
| [`LICENSE`](LICENSE) | Apache 2.0 |

---

## Versioning

This module follows [SemVer](https://semver.org). The single source of truth
is the [`VERSION`](VERSION) file at the module root. When you copy this
module into a new project, **leave `VERSION` untouched** so you can later
diff against the upstream copy to know which bug fixes you're missing.

Public API surface guarded by SemVer:
- `AnalyticsModule.init / setLogMode / setBotInfo / setWebhookInfo / setWebhookSender / setEventConfigs / setEnabled / getApplication / getAppOpenedTimestamp`
- `AnalyticsEvents.logScreenViewEv / logClickBtnEv / logShowRateDialogEv / logTimeOpenAppEv / logLoadAdEv / logShowAdEv / logClickAdEv / logOpenAppFromEv / logPaidAdImpressionEv / logIAPEv / logAppExitEv / logOnboardingStepEv / logEvent`
- `AnalyticsUserProperties.logAdEngagementLevel / logHasSeenTutorial / logCountry / logLanguageAndAppVersion / logUserTier / logSubscriptionStatus / logEventScreenOpen / logEventAllowPermission / logEventAllowNotification`
- All `data class` event models in `models/`
- `WebhookSender`, `AdRevenueLike`, `AnalyticsEvent` interfaces
- `TestLogMode`, `AllowPermission`, `AdResult`, `EventConfigs`
