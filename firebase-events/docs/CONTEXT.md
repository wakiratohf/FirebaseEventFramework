# Domain language — `firebase-events`

This document defines the vocabulary the SDK uses. When you write project
event catalogs, follow these terms so the codebase stays consistent across
projects.

## Glossary

| Term | Definition |
|---|---|
| **Event** | A single named action that gets logged to Firebase Analytics. Carries an optional `Bundle` of typed parameters. |
| **User property** | A long-lived key/value on the user record (set once or rarely, persists across sessions). Use for segmentation, not for action counting. |
| **Screen name** | A stable, **camelCase** identifier for a UI screen (e.g. `home`, `radar`, `settingsNotification`). Used as the `screen_name` parameter on `screen_view_ev` and prefixes `click_btn_ev`. |
| **Popup name** | A stable, **camelCase** identifier for an overlay/dialog within a screen (e.g. `rateDialog`, `permissionRequest`). Empty when no popup is active. |
| **Button name** | A stable, **camelCase** identifier for a tappable element. NO `_`, NO `btn` prefix (e.g. `refresh`, `subscribe`, `sortConfirm` — NOT `btn_refresh`). See [`NAMING_CONVENTION.md`](NAMING_CONVENTION.md) — enforced by Lint rule `:firebase-events-lint`. |
| **Session** | The time window from when the user foregrounds the app until it goes to background (or is killed). The SDK does not manage sessions itself — the host app passes a `sessionProvider` lambda to [`AnalyticsModule.init`](../src/main/java/com/tohsoft/firebase_events/AnalyticsModule.kt) that returns "time since app foregrounded" in millis. |
| **App open** | Foregrounding the app (cold start OR resuming from background). Bumps the `appOpenedCount` counter. |
| **Engagement level** | Bucketed count of ad clicks per user. Logged only when the count hits one of the threshold values: 0, 1, 5, 10, 20, 50, 100, 200, 500. |
| **Test-log mode** | Routes event payloads to a side channel (Telegram or webhook) instead of (or alongside) Firebase. Used during QA so testers can see exactly what the app is sending. |
| **Master kill-switch** | `AnalyticsModule.isEnabled`. When `false`, all log calls early-return. Use to honour GDPR/CCPA consent. |

## Naming conventions

### Event names

- Lowercase snake_case.
- ≤ 40 characters, must start with a letter, then `[A-Za-z0-9_]*`.
- Don't include the project slug — events are scoped per Firebase property already.
- Suffix `_ev` for events that the SDK ships (e.g. `screen_view_ev`, `click_btn_ev`). Project-defined events may omit the suffix.

### Param keys

- Lowercase snake_case, ≤ 40 chars.
- Avoid reusing Firebase-reserved keys (`firebase_event_origin`, `firebase_screen`, etc.).

### Param values

- Strings: ≤ 100 chars.
- Booleans: prefer `"0"` / `"1"` strings (Firebase's BigQuery export treats them more consistently than bool params).
- Timestamps: prefer epoch seconds (`Int`) over formatted strings — easier to range-query.

### Screen / popup / button names

- **camelCase, no `_`, no spaces.** See [`NAMING_CONVENTION.md`](NAMING_CONVENTION.md) for the full spec and the rationale.
- `_ScreenViewEv` / `_ClickBtnEv` run the input through [`Strings.convertSnakeCaseToCamelCase`](../src/main/java/com/tohsoft/firebase_events/utils/Strings.kt). Both snake_case and camelCase produce the same PascalCase output, but only **camelCase input** is the supported convention — `:firebase-events-lint` rejects underscored values and `btn` prefixes.
- Constants (LHS of `const val`) stay SCREAMING_SNAKE_CASE per Kotlin idiom; only the **value** (RHS) is camelCase.

## What the SDK is opinionated about

- **Sealed class hierarchies for events: NO.** Each event is an independent
  `data class` with `toBundle()`. New events should implement
  [`AnalyticsEvent`](../src/main/java/com/tohsoft/firebase_events/models/AnalyticsEvent.kt)
  but the existing ones won't be retrofitted (backward compat).
- **Builder pattern: NO.** Constructors are fine; events are small.
- **DI framework: NO.** Singletons (`object`) + lambda providers passed to
  `AnalyticsModule.init`. The SDK plays nicely with Hilt/Koin in the host app
  but has no opinion about which one.
- **EventBus: NO.** The SDK does not subscribe to or publish on any event bus.
  Call its methods directly, or hide them behind your own helper.
