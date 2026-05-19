# Migration notes

Notes for upgrading an in-tree copy of `:firebase-events` to a newer SDK
version. Read top-down — the most recent version is at the top.

## 1.0.0 — Initial public release (2026-05)

This is the first version of the SDK suitable for copy-pasting into other
projects. Compared to the original in-tree module that lived in `toh-weather`
prior to this release, the following changed:

### Removed internal-module dependencies

| Previous dep | What replaced it | What the host app needs to do |
|---|---|---|
| `project(":TOH-Ads-SDK")` — used by `ShowAdEv`, `PaidAdImpressionEv` | Local [`AdResult`](../src/main/java/com/tohsoft/firebase_events/models/AdResult.kt) constants + [`AdRevenueLike`](../src/main/java/com/tohsoft/firebase_events/models/AdRevenueLike.kt) adapter interface | If you were passing a concrete `AdRevenue` to `PaidAdImpressionEv`, wrap it in an `AdRevenueLike` implementation. In the reference project, see [`AdRevenueLikeAdapter.kt`](../../app/src/main/java/com/tohsoft/weather/event/AdRevenueLikeAdapter.kt). |
| `project(":webhook")` — used by `AnalyticsModule`, `TestLogHelper` | [`WebhookSender`](../src/main/java/com/tohsoft/firebase_events/utils/WebhookSender.kt) interface, injected via `AnalyticsModule.setWebhookSender(...)` | If you used `WEBHOOK` test-log mode, write a thin adapter that implements `WebhookSender` and call `setWebhookSender` before `init`. See [`ChatangoWebhookSender.kt`](../../app/src/main/java/com/tohsoft/weather/event/ChatangoWebhookSender.kt) for the reference project's adapter. If you never used webhook test-mode, do nothing — `TestLogMode.WEBHOOK` silently no-ops without a registered sender. |
| `project(":utils:toh-utility")` — used by `AnalyticsEvents.logOpenAppFromEv` for `UtilsLib.getDateTime` | Inlined [`DateTimeHelper.currentHour24()`](../src/main/java/com/tohsoft/firebase_events/utils/DateTimeHelper.kt) | Nothing. Output is identical (current hour `0–23`). |

### Public API: added

| Surface | Purpose |
|---|---|
| `AnalyticsModule.setWebhookSender(WebhookSender)` | Register a webhook transport |
| `AnalyticsModule.getWebhookSender(): WebhookSender?` | Inspect the current sender |
| `AnalyticsModule.setEnabled(Boolean)` + `isEnabled` | Master kill-switch for GDPR/CCPA consent |
| `AnalyticsEvents.logEvent(event: AnalyticsEvent, isFirebaseEventsEnable)` | Log a project-defined event implementing the interface |
| `interface AnalyticsEvent` in `models/` | Contract for project-defined events |
| `interface AdRevenueLike` in `models/` | Adapter for the `PaidAdImpressionEv` payload |
| `interface WebhookSender` in `utils/` | Adapter for `TestLogMode.WEBHOOK` |
| `object AdResult` in `models/` | Local mirror of the previous `com.tohsoft.ads.models.AdResult` constants |
| `utils/EventNameValidator` | Soft validation against the Firebase 40/40/100-char limits (debug only) |
| `utils/DateTimeHelper` | Lightweight date/hour helpers |

### Public API: changed (non-breaking for in-tree users)

- `PaidAdImpressionEv(adRevenue: AdRevenue)` → `PaidAdImpressionEv(adRevenue: AdRevenueLike)`.
  The previous concrete `AdRevenue` from `:TOH-Ads-SDK` no longer compiles
  here; wrap it in an `AdRevenueLike` adapter at the call site.
- `AnalyticsEvents.logEvent(eventName, params, isFirebaseEventsEnable)`
  signature is unchanged, but now guards on `AnalyticsModule.isEnabled` and
  runs the soft validator in test mode.
- `AnalyticsModule.setWebhookInfo(...)` is unchanged for callers, but now
  delegates to the registered `WebhookSender` instead of calling `WebhookApi`
  directly. If no sender is registered, the call still persists the creds to
  prefs (so a later `setWebhookSender` picks them up automatically).

### Behavioural change: `setWebhookSender`

`setWebhookSender` no longer calls `sender.initialize(...)` as a side effect.
It now only attaches the sender. The transport is initialized later, either:

- by [`AnalyticsModule.init`](../src/main/java/com/tohsoft/firebase_events/AnalyticsModule.kt) (using persisted credentials), or
- by [`AnalyticsModule.setWebhookInfo`](../src/main/java/com/tohsoft/firebase_events/AnalyticsModule.kt) (using fresh credentials).

Reason: hosts that re-run their `initAnalytics()` block per session (e.g. once
from `Application.onCreate`, then again from `MainActivity.onActivityCreated`)
previously triggered `WebhookSender.initialize` twice per call — once from
`setWebhookSender`, once from `init`. The Chatango-backed implementation in
this repo sends a `"login"` sentinel event from `initialize`, so the doubled
calls were doubling the noise on the QA channel.

Same-instance re-registration is now a cheap no-op. Different instances still
replace the previous sender. Covered by `AnalyticsModuleWebhookSenderTest`.

### Public API: unchanged

All other `logXxxEv` methods and user-property setters keep their previous
signatures.

### Compile-time impact for the `toh-weather` reference project

- New file: [`app/src/main/java/com/tohsoft/weather/event/AdRevenueLikeAdapter.kt`](../../app/src/main/java/com/tohsoft/weather/event/AdRevenueLikeAdapter.kt)
- New file: [`app/src/main/java/com/tohsoft/weather/event/ChatangoWebhookSender.kt`](../../app/src/main/java/com/tohsoft/weather/event/ChatangoWebhookSender.kt)
- Changed: [`AbsLifeCycleApplication.initAnalytics()`](../../app/src/main/java/com/tohsoft/weather/AbsLifeCycleApplication.kt) calls `AnalyticsModule.setWebhookSender(ChatangoWebhookSender)` before `init`.
- Changed: [`LogAdsEventHelper.logAdPaidEvent`](../../app/src/main/java/com/tohsoft/weather/event/LogAdsEventHelper.kt) wraps `AdRevenue` in `AdRevenueLikeAdapter`.

No other call sites in `:app` need changes.
