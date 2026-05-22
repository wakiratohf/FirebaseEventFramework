# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository purpose

This repo is the **upstream / reference** copy of the `firebase-events` SDK and its optional companion modules, plus a minimal `:app` demo. The library modules are designed to be **copy-pasted** into other Android projects (see each module's `README.md`). The five modules play very different roles:

| Module | Role | Notes |
|---|---|---|
| `:firebase-events` | Core SDK (public, SemVer) | `minSdk 21`, JDK 17. **Zero internal `project(":...")` deps** so it lifts cleanly into other repos. Bump `firebase-events/VERSION` when you change the public surface enumerated in `firebase-events/README.md`. |
| `:firebase-events-lint` | Lint rules shipped with the SDK | Pure-JVM (JDK 17). Enforces the `buttonName` convention at compile time. Copy alongside `:firebase-events`. |
| `:app-events` (`com.tohsoft.app_event`) | App-level wrappers (optional) | `minSdk 21`, JDK 17. Lifecycle/intent/ads helpers that wrap Android plumbing around `:firebase-events`. Depends on `:firebase-events`. |
| `:TOH-Ad` (`com.tohsoft.ad`) | TOHSOFT ads library + analytics bridge (optional) | `minSdk 23`, JDK 17, Compose-enabled. The real TOHSOFT ads library (AdMob + UMP: banner/interstitial/app-open/OPA), vendored from the toh-vpn project. The only module that knows the concrete ad SDK. The analytics bridge in `com.tohsoft.ads.analytics` forwards ad callbacks into `AdsEventTracker` (banner-only for now). Depends on `:firebase-events` + `:app-events`. |
| `:app` (`com.example.firebaseeventframework`) | Demo / smoke-test host | `minSdk 23`, JDK 11, Compose. Exercises the SDK only; not shipped. Don't put reusable abstractions here. |

Dependency direction is strict: everything may depend **on** `:firebase-events`, never the reverse. `:firebase-events-lint` is wired in via `lintPublish` (bundled into the SDK AAR) and `lintChecks` (in `:app`) — these are build-time, not runtime, deps, so they don't violate the SDK's zero-dependency contract.

## Common commands

Multi-module Gradle, Kotlin 2.0.20, AGP 9.2.1. Library modules target JDK 17; `:app` targets JDK 11. Use the wrapper. Versions are centralized in `gradle/libs.versions.toml` (note the documented rule there: `lint-api` version = AGP version + 23.0.0).

```bash
# Build
./gradlew :firebase-events:assembleDebug
./gradlew :app-events:assembleDebug
./gradlew :TOH-Ad:assembleDebug
./gradlew :app:assembleDebug

# Unit tests — the SDK's tests live in :firebase-events; lint rules are tested in :firebase-events-lint.
# :app-events and :app carry only stub tests.
./gradlew :firebase-events:testDebugUnitTest
./gradlew :firebase-events-lint:test

# Single test class / method
./gradlew :firebase-events:testDebugUnitTest --tests "com.tohsoft.firebase_events.utils.EventNameValidatorTest"
./gradlew :firebase-events:testDebugUnitTest --tests "*.AnalyticsModuleWebhookSenderTest.someMethod"
./gradlew :firebase-events-lint:test --tests "*.ButtonNameConventionDetectorTest"

# Lint (the SDK's published rules run against :app via lintChecks)
./gradlew :firebase-events:lintDebug
./gradlew :app:lintDebug

# Install demo on a device and turn on Firebase DebugView for it
./gradlew :app:installDebug
adb shell setprop debug.firebase.analytics.app com.example.firebaseeventframework
adb logcat -s AnalyticsEvents:V AnalyticsValidator:V
```

The SDK's `testOptions.unitTests.isReturnDefaultValues = true` is intentional — unit tests run on JVM and stub out `android.util.Log` / `Bundle`. **Never write tests in `:firebase-events/src/test` that exercise `Bundle` semantics**; those belong in `androidTest` or rely on the test's own fake. The existing tests target pure-Kotlin classes (`EventNameValidator`, `DateTimeHelper`, `Strings`, model `toBundle()` round-trips with stubbed Bundle defaults).

## Architecture (the part that needs reading several files to grasp)

### Three-layer split inside `:firebase-events`

1. **Core singletons** — `AnalyticsModule` (init + master kill-switch + transport injection), `AnalyticsEvents` (typed log methods + generic overload), `AnalyticsUserProperties` (long-lived segmentation keys). All `object`s — no DI framework, no builder pattern. Initialization is a single call in `Application.onCreate` taking lambda providers (`appProvider`, `sessionProvider`).
2. **`models/`** — one `data class` per event type with a `toBundle(): Bundle`. Built-in events are prefixed with `_` (e.g. `_ScreenViewEv`, `_ClickBtnEv`) and do **not** implement the `AnalyticsEvent` interface — that's intentional, backward-compat. New events from the host app should implement `AnalyticsEvent` and use the generic `AnalyticsEvents.logEvent(event)` overload. Don't retrofit `AnalyticsEvent` onto the `_`-prefixed classes.
3. **`utils/`** — transports (`TelegramBot`, `WebhookSender` interface), persistence (`FirebasePrefs` over SharedPreferences), helpers (`DateTimeHelper`, `Strings`, `EventNameValidator`), test-log routing (`TestLogHelper`).

### Three sideways concerns wired through `AnalyticsModule`

- **Master kill-switch** — `AnalyticsModule.isEnabled`. Every `logEvent` early-returns if false. The SDK does **not** persist this; the host app must store the user's consent choice and re-apply on each `init` (see `app/.../DemoApp.kt`).
- **`EventConfigs`** — per-event-type boolean toggle held by `AnalyticsEvents`. Serialized as JSON to prefs; auto-restored on next `init`. Push new configs via Firebase Remote Config + `AnalyticsModule.setEventConfigs(ctx, cfg)`.
- **`TestLogMode`** (`NONE` / `TELEGRAM` / `WEBHOOK`) — controls a side-channel mirror used during QA. When `AnalyticsModule.isTestMode == true`, events are **only** dumped to Logcat + the side channel; they are **not** sent to Firebase. This is critical when reasoning about event flow.

### Webhook adapter pattern

The SDK ships a `WebhookSender` *interface*, not an implementation. `setWebhookSender(sender)` only attaches the instance — `sender.initialize(...)` is called from `AnalyticsModule.init` (using persisted credentials) or `setWebhookInfo` (using fresh credentials). Same-instance re-registration is a deliberate no-op. If no sender is registered, `TestLogMode.WEBHOOK` silently drops events; Telegram mode still works because it's built in.

Same adapter pattern for ad revenue: `AdRevenueLike` is an interface so the SDK never depends on a specific ad-SDK class.

### What the `:app` demo demonstrates

The demo encodes the patterns each host project is expected to copy:

- `DemoApp.kt` — Application init with `ActivityLifecycleCallbacks` for session timing + consent persistence + initial user-property log.
- `event/` package (`ScreenName`, `ButtonName`, `PopupName`, `AnalyticsEventsUtils`) — the **project event catalog**, which `firebase-events/docs/PROJECT_EVENT_TEMPLATE.md` says must live in the host app, NOT in the SDK. Don't move these constants into `:firebase-events`.
- `ui/base/BaseTrackedActivity.kt` — automated `screen_view_ev` via `onResume`/`onPause`. Subclasses only override `screenName()`.

### `:app-events` — Android plumbing layer (high-level vs low-level API)

`:firebase-events` is deliberately framework-free Firebase logging. Anything that touches `android.content.Intent`, `Activity`/process lifecycle, or `PendingIntent` belongs in `:app-events` instead, which dispatches down to a `:firebase-events` log method. It stays generic — project-specific enums/screen names still belong in the host app, not here. Trackers: `OpenAppFromIntent` (`open_app_from_ev`), `TimeOpenAppTracker` (`time_open_app_ev`), `AppExitTracker` (`app_exit`, debounced via a 1s coroutine delay), `AdsEventTracker` (ad events, SDK-agnostic).

Each lifecycle event exposes **two mutually exclusive** entry points — never wire both, or you double-log:
- **High-level** — `AppEventsInstaller.install(application)`, one line in `Application.onCreate()`. Registers its own `ActivityLifecycleCallbacks` + `ProcessLifecycleOwner` observer. `lifecycle-process` is imported *only* by the installer.
- **Low-level** — pure functions (`TimeOpenAppTracker.onSessionStart()`, `AppExitTracker.onAppExit()` / `setLastActiveScreen()`) you call from your own lifecycle code, for edge-case triggers.

### `:TOH-Ad` — the only SDK-aware module

`:TOH-Ad` is the real TOHSOFT ads library (vendored from the toh-vpn project), the single place that depends on a concrete ad SDK (Google Mobile Ads / AdMob + UMP consent, exposed via `api`). Its core (`AdsConfig`, `AdsModule`, the `wrapper/` ad types) lives under package `com.tohsoft.ads`; the analytics glue lives under `com.tohsoft.ads.analytics`.

The bridge forwards the library's ad callbacks into `AdsEventTracker` (which lives in `:app-events` and is itself ad-SDK-agnostic) and into the `AdRevenueLike` adapter (which lives in `:firebase-events`). Because the upstream library only surfaced `onAdStartLoad/onAdLoaded/onAdFailedToLoad/onAdClicked/onAdOpened/onAdClosed`, two hooks were **added to the vendored copy** to keep all four analytics events: `AdWrapperListener.onAdImpression()` (→ `show_ad_ev`) and `onPaidEvent(adValue, adUnitId, adSource)` (→ `paid_ad_impression`), wired in `AdViewWrapper`'s bottom-banner path. Bridging is **banner-only** for now; interstitial/app-open/OPA are unbridged.

The analytics package owns: `AdsAnalytics` (init + `attachBanner` listener factory), `TohAdRevenue` (`AdRevenueLike` adapter for AdMob `AdValue`), `AdType`/`AdAction` constants, and a Compose `BannerAd` wrapper (hence Compose is enabled in this module's build). Keep ad-SDK-specific glue here, out of `:app` — the host app only calls `AdsConfig`/`AdsModule`/`AdsAnalytics.init` and drops `BannerAd` into its UI.

### `:firebase-events-lint` — compile-time convention enforcement

Pure-JVM lint module. `ButtonNameConventionDetector` flags any enum implementing a `ClickBtnEv`-style interface (any package; matched structurally by an interface named `ClickBtnEv` whose 2nd constructor arg is `buttonName`) when `buttonName` is empty, contains `_`, or starts with the `btn` prefix. `ClickBtnEvIssueRegistry` registers the issues. The jar is `lintPublish`-bundled into the `:firebase-events` AAR and applied to `:app` via `lintChecks`. Lint runs are tested in `ButtonNameConventionDetectorTest`, not at app build time.

### Where authoritative docs live

`firebase-events/docs/` is the source of truth for the SDK's contract — when behavior changes, update the matching doc in the same commit:

- `CONTEXT.md` — glossary + naming conventions (lowercase snake_case, ≤ 40 char event names, ≤ 100 char string values).
- `NAMING_CONVENTION.md` — the in-depth naming-rules reference (event/param/value rules the runtime validator and lint enforce).
- `INTEGRATION.md` / `FRESH_PROJECT_GUIDE.md` — copy-paste setup guides (the latter is Vietnamese, more thorough).
- `EVENT_CATALOG.md` — bundle schema for every built-in event + user property.
- `PROJECT_EVENT_TEMPLATE.md` — the contract for `:app`-side catalogs.
- `CONFIGURATION.md` — `EventConfigs`, Remote Config, `TestLogMode`, consent.
- `MIGRATION.md` — bumps per VERSION.

`:app-events/docs/` is the contract for that module's helpers (`INTEGRATION.md` plus a per-event guide for each tracker: `OPEN_APP_FROM_GUIDE.md`, `TIME_OPEN_APP_GUIDE.md`, `APP_EXIT_GUIDE.md`, `ADS_EVENT_GUIDE.md`, `SCREEN_VIEW_GUIDE.md`, `RATE_DIALOG_GUIDE.md`, `DIALOG_SCREEN_VIEW_LOGGING.md`). Update these in the same commit as a behavior change, and bump `app-events/VERSION` for its public surface.

## Conventions specific to this repo

- `:firebase-events` has **zero runtime `project(":...")` dependencies** by design (so it can be lifted into other repos). Use the adapter interfaces (`WebhookSender`, `AdRevenueLike`, `AnalyticsEvent`) instead of reaching into another module. The lone exception is `lintPublish(project(":firebase-events-lint"))`, a build-time configuration that bundles lint rules into the AAR — not a runtime dep.
- `:app-events` → `:firebase-events`; `:TOH-Ad` → `:app-events` + `:firebase-events`; `:app` → all three (+ `lintChecks` on the lint module). Never invert these arrows.
- Event names use lowercase snake_case, ≤ 40 chars, start with a letter. SDK-shipped events suffix `_ev`. Param keys ≤ 40 chars; string values ≤ 100 chars. Violations are flagged at runtime by `EventNameValidator` only when `isTestMode == true`; the `buttonName` convention is additionally enforced at compile time by `:firebase-events-lint`.
- Library modules `:firebase-events` / `:app-events` target JDK 17, `minSdk 21`, `compileSdk 36`. `:TOH-Ad` is JDK 17 / `compileSdk 36` but `minSdk 23` (the upstream library was `minSdk 24`, lowered to match `:app` and avoid a manifest-merger conflict). `:app` targets JDK 11, `minSdk 23`. Don't unify the lower-minSdk libraries with `:app` without reason — their lower minSdk is part of the portability contract.
- Don't commit real `google-services.json` files for downstream projects. The one in `app/` here is for the demo only.
