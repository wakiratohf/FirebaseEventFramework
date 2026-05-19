# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository purpose

This repo is the **upstream / reference** copy of the `firebase-events` SDK plus a minimal `:app` demo. The SDK itself is designed to be **copy-pasted** into other Android projects (see `firebase-events/README.md`). Treat the two modules differently:

- `:firebase-events` — public, SemVer-guarded library. Public API surface is enumerated in `firebase-events/README.md` ("Versioning" section). Bump `firebase-events/VERSION` when you change that surface.
- `:app` (`com.example.firebaseeventframework`) — demo / smoke-test host. Lives only to exercise the SDK; not shipped. Don't put reusable abstractions here.

## Common commands

Single-module Gradle, JDK 17, Kotlin 2.0.20, AGP 9.2.1. Use the wrapper.

```bash
# Build
./gradlew :firebase-events:assembleDebug
./gradlew :app:assembleDebug

# All JVM unit tests (the SDK's tests live here — :app has only stub tests)
./gradlew :firebase-events:testDebugUnitTest

# Single test class / method
./gradlew :firebase-events:testDebugUnitTest --tests "com.tohsoft.firebase_events.utils.EventNameValidatorTest"
./gradlew :firebase-events:testDebugUnitTest --tests "*.AnalyticsModuleWebhookSenderTest.someMethod"

# Lint
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

### Firebase delivery is currently disabled — read before changing event flow

Two production code paths into Firebase are commented out under a `// Radar ko log event & properties` marker:

- `AnalyticsEvents.flushEvents` (`AnalyticsEvents.kt`) — the `GlobalScope.launch { FirebaseAnalytics.getInstance(context).logEvent(...) }` body is fully commented. `logEvent` still buffers up to 5 items and calls `flushEventsImmediate()`, but the flush is a no-op. So `isTestMode == false` builds neither send events to Firebase **nor** clear the buffer through to a transport — they just grow the list forever.
- `AnalyticsUserProperties.logUserPropertyEv` (`AnalyticsUserProperties.kt`) — the entire body (test-mode dump + `FirebaseAnalytics.setUserProperty`) is commented. Every `AnalyticsUserProperties.logXxx` call is effectively a no-op past the `isEnabled` early-return, in **both** test and production modes.

The "Radar" comment suggests a downstream project deliberately switched these off. Before re-enabling, check with the user — don't assume it's a bug. If you're debugging "why don't I see events / user properties in DebugView," this is almost certainly the reason and not your wiring.

### What the `:app` demo demonstrates

The demo encodes the patterns each host project is expected to copy:

- `DemoApp.kt` — Application init with `ActivityLifecycleCallbacks` for session timing + consent persistence + initial user-property log.
- `event/` package (`ScreenName`, `ButtonName`, `PopupName`, `AnalyticsEventsUtils`) — the **project event catalog**, which `firebase-events/docs/PROJECT_EVENT_TEMPLATE.md` says must live in the host app, NOT in the SDK. Don't move these constants into `:firebase-events`.
- `ui/base/BaseTrackedActivity.kt` — automated `screen_view_ev` via `onResume`/`onPause`. Subclasses only override `screenName()`.

### Where authoritative docs live

`firebase-events/docs/` is the source of truth for the SDK's contract — when behavior changes, update the matching doc in the same commit:

- `CONTEXT.md` — glossary + naming conventions (lowercase snake_case, ≤ 40 char event names, ≤ 100 char string values).
- `INTEGRATION.md` / `FRESH_PROJECT_GUIDE.md` — copy-paste setup guides (the latter is Vietnamese, more thorough).
- `EVENT_CATALOG.md` — bundle schema for every built-in event + user property.
- `PROJECT_EVENT_TEMPLATE.md` — the contract for `:app`-side catalogs.
- `CONFIGURATION.md` — `EventConfigs`, Remote Config, `TestLogMode`, consent.
- `MIGRATION.md` — bumps per VERSION.

## Conventions specific to this repo

- The SDK module has **zero `project(":...")` dependencies** by design (so it can be lifted into other repos). Don't add inter-module deps — use the adapter interfaces (`WebhookSender`, `AdRevenueLike`, `AnalyticsEvent`) instead.
- Event names use lowercase snake_case, ≤ 40 chars, start with a letter. SDK-shipped events suffix `_ev`. Param keys ≤ 40 chars; string values ≤ 100 chars. Violations are flagged at runtime by `EventNameValidator` only when `isTestMode == true`.
- `:firebase-events` targets `JDK 17`, `minSdk 21`, `compileSdk 36`. `:app` targets `JDK 11`, `minSdk 23`. Don't unify them without reason — the SDK's lower minSdk is part of its portability contract.
- Don't commit real `google-services.json` files for downstream projects. The one in `app/` here is for the demo only.
