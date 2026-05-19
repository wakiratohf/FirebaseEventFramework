# app-event

App-level event helpers that wrap Android plumbing (intents, lifecycle,
shortcuts) around the pure Firebase log layer in `:firebase-events`.

**Version:** see [`VERSION`](VERSION) · **minSdk 21 · compileSdk 35 · JDK 17**

## Why this module exists

`:firebase-events` is intentionally framework-free Firebase logging:
event names, bundle formatters, Remote Config toggles, transports. Anything
that touches `android.content.Intent`, `Activity` lifecycle, or app-specific
enums belongs **here** instead. That keeps `:firebase-events` copy-pasteable
across products without dragging Android UI assumptions along.

## What's inside

| Capability | Where |
|---|---|
| `open_app_from_ev` wiring (intent builder + dispatcher) | [`OpenAppFromIntent`](src/main/java/com/tohsoft/app_event/OpenAppFromIntent.kt) |
| `OpenAppSource` interface for per-app enums | [`OpenAppSource`](src/main/java/com/tohsoft/app_event/OpenAppSource.kt) |
| `time_open_app_ev` lifecycle helper | [`TimeOpenAppTracker`](src/main/java/com/tohsoft/app_event/TimeOpenAppTracker.kt) |
| `app_exit` lifecycle helper (debounced, last-screen aware) | [`AppExitTracker`](src/main/java/com/tohsoft/app_event/AppExitTracker.kt) |
| One-line drop-in installer for both lifecycle events | [`AppEventsInstaller`](src/main/java/com/tohsoft/app_event/AppEventsInstaller.kt) |
| Wiring this module | [`docs/INTEGRATION.md`](docs/INTEGRATION.md) |
| Per-event recipes | [`docs/OPEN_APP_FROM_GUIDE.md`](docs/OPEN_APP_FROM_GUIDE.md) · [`docs/TIME_OPEN_APP_GUIDE.md`](docs/TIME_OPEN_APP_GUIDE.md) · [`docs/APP_EXIT_GUIDE.md`](docs/APP_EXIT_GUIDE.md) |

## Choosing high-level vs low-level API

For the two lifecycle events (`time_open_app_ev`, `app_exit`):

- **High-level — `AppEventsInstaller.install(application)`**. One line in
  `Application.onCreate()`. The installer registers its own
  `ActivityLifecycleCallbacks` + `ProcessLifecycleOwner` observer; both fire
  alongside any callbacks your app already has. Recommended for new projects
  with no special lifecycle requirements.
- **Low-level — `TimeOpenAppTracker.onSessionStart()`,
  `AppExitTracker.onAppExit()`, `AppExitTracker.setLastActiveScreen()`**. Pure
  functions you call from your own lifecycle code. Use when you need precise
  control or have edge-case triggers (e.g. `toh-weather` fires `onSessionStart`
  on a full-screen overlay dismiss in addition to `ON_START`).

Do not mix the two — using both produces duplicate events. Each event's guide
shows four patterns (installer + three explicit-call shapes); pick one per
project.

## Dependencies

- `:firebase-events` — the log layer this module dispatches to.
- `androidx.core.ktx` — for the `android.content.Intent` extensions used by
  `OpenAppFromIntent`.
- `kotlinx-coroutines-android` — `AppExitTracker` debounces back-to-back exit
  triggers with a 1s coroutine delay.
- `androidx.lifecycle:lifecycle-process` — only `AppEventsInstaller` imports it
  (observes `ProcessLifecycleOwner`). The tracker classes themselves are
  lifecycle-agnostic; if you stick to the low-level API and your own lifecycle
  plumbing, you don't need to pull this transitively.

## Adding new helpers

Anything that meets all of these is a fit for `:app-event`:

1. Wraps Android intent / lifecycle / `PendingIntent` plumbing.
2. Ultimately calls a `:firebase-events` log method.
3. Stays generic across products (no project-specific enums or screen names).

If a helper needs project-specific knowledge (e.g. a weather-only screen
name catalog), it belongs in `:app`, not here.
