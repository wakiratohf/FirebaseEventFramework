# Project event catalog — template

This doc shows the pattern for adding project-specific events without forking
the SDK. The SDK supplies the *infrastructure* (types, transports, kill-switch).
Your app supplies the *vocabulary* (screen names, button names, dialog names).

> The reference implementation in the `toh-weather` repo lives at
> [`app/src/main/java/com/tohsoft/weather/event/`](../../app/src/main/java/com/tohsoft/weather/event/) — read alongside this template.

---

## Why catalogs belong in `:app`, not in the SDK

- Screen names like `home_radar` or `gallery_album_picker` are tightly coupled
  to your app's navigation graph. They have no meaning in another project.
- Forking the SDK to add an enum entry would diverge every project from upstream.
- The SDK already provides generic event *types*. You only need to provide the
  *values*.

---

## File layout in your app module

```
app/src/main/java/<your.package>/event/
├── ClickBtnEv.kt              ← marker interface (3 props) for Lint rule
├── ScreenName.kt              ← screen constants: "home", "radar", ...
├── PopupName.kt               ← popup constants: "rateDialog", ...
├── HomeBtnEv.kt               ← one enum PER screen, implements ClickBtnEv
├── RadarBtnEv.kt              ← idem for radar
├── AnalyticsEventsUtils.kt    ← thin wrapper around SDK calls
├── ScreenViewEventHelper.kt   ← optional: automate screen-view tracking
└── LogAdsEventHelper.kt       ← optional: automate ad-event tracking
```

> Pattern shift: prefer **one enum per screen implementing `ClickBtnEv`**
> over a single flat `object ButtonName` of `const val`. The enum form is
> what `:firebase-events-lint`'s `ButtonNameConventionDetector` inspects;
> a flat object cannot be lint-checked the same way.

---

## Step 1 — Declare interface marker + catalog

```kotlin
// ClickBtnEv.kt — REQUIRED for the Lint rule to fire
package your.package.event

interface ClickBtnEv {
    val screenName: String
    val buttonName: String   // camelCase, no "_", no "btn" prefix
    val popupName: String    // "" when no popup
}

// ScreenName.kt — camelCase values (see NAMING_CONVENTION.md)
object ScreenName {
    const val HOME = "home"
    const val RADAR = "radar"
    const val DAILY = "daily"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
}

// PopupName.kt
object PopupName {
    const val RATE_DIALOG = "rateDialog"
    const val PERMISSION_REQUEST = "permissionRequest"
    const val NONE = ""
}

// HomeBtnEv.kt — one enum per screen, NOT one global enum
enum class HomeBtnEv(
    override val screenName: String,
    override val buttonName: String,
    override val popupName: String,
) : ClickBtnEv {
    REFRESH(ScreenName.HOME, "refresh", ""),
    SUBSCRIBE(ScreenName.HOME, "subscribe", ""),
    SEARCH_LOCATION(ScreenName.HOME, "searchLocation", ""),
    RATE_OK(ScreenName.HOME, "ok", PopupName.RATE_DIALOG),
}
```

If a buttonName value violates the convention (`"btn_refresh"`,
`"search_location"`, `"Refresh"`, `""`), `:app:lintDebug` will fail
the build with one of the 4 issue IDs `ClickBtnEvUnderscore` /
`ClickBtnEvBtnPrefix` / `ClickBtnEvNotCamelCase` / `ClickBtnEvEmpty`.
See [`NAMING_CONVENTION.md`](NAMING_CONVENTION.md) for the full
spec.

---

## Step 2 — Write a thin wrapper

The wrapper is your app's "anti-corruption layer" between UI code and the SDK.
It keeps call sites short and gives you a place to add app-specific logic
(default screen-name detection, build-flavour gating, etc.) without touching
the SDK.

```kotlin
// AnalyticsEventsUtils.kt
package your.package.event

import com.tohsoft.firebase_events.AnalyticsEvents
import com.tohsoft.firebase_events.models._ClickBtnEv

object AnalyticsEventsUtils {

    fun logClickBtn(screenName: String, buttonName: String, popupName: String = "") {
        AnalyticsEvents.logClickBtnEv(
            _ClickBtnEv(
                screenName = screenName,
                buttonName = buttonName,
                popupName = popupName,
                time = secondsSinceAppOpen()
            )
        )
    }

    private fun secondsSinceAppOpen(): Int {
        val ts = com.tohsoft.firebase_events.AnalyticsModule.getAppOpenedTimestamp() ?: return 0
        return ((System.currentTimeMillis() - ts) / 1000).toInt()
    }
}
```

Reference: [`AnalyticsEventsUtils.kt`](../../app/src/main/java/com/tohsoft/weather/utils/extensions/AnalyticsEventsUtils.kt).

---

## Step 3 — Use it

Pass the enum constant rather than separate strings — that way any
convention drift is caught at the enum declaration site (by Lint),
not at every call site.

```kotlin
binding.btnRefresh.setOnClickListener {
    AnalyticsEventsUtils.logClickBtn(
        screenName = HomeBtnEv.REFRESH.screenName,
        buttonName = HomeBtnEv.REFRESH.buttonName,
        popupName  = HomeBtnEv.REFRESH.popupName,
    )
    refreshWeather()
}
```

---

## Step 4 (optional) — Define a project-specific event type

If you need an event that the SDK doesn't ship (e.g. `widget_resized`), don't
modify the SDK. Implement
[`AnalyticsEvent`](../src/main/java/com/tohsoft/firebase_events/models/AnalyticsEvent.kt)
in your app and pass it to the generic `logEvent` overload.

```kotlin
// WidgetResizedEv.kt — in your app module
package your.package.event

import android.os.Bundle
import com.tohsoft.firebase_events.models.AnalyticsEvent

data class WidgetResizedEv(
    val widgetId: Int,
    val newWidth: Int,
    val newHeight: Int
) : AnalyticsEvent {
    override val eventName: String = "widget_resized"
    override fun toBundle(): Bundle = Bundle().apply {
        putInt("widget_id", widgetId)
        putInt("new_width", newWidth)
        putInt("new_height", newHeight)
    }
}
```

```kotlin
AnalyticsEvents.logEvent(WidgetResizedEv(widgetId = 42, newWidth = 4, newHeight = 2))
```

This honours the master kill-switch, the soft validator, and the test-log
modes the same way the built-in events do.

---

## Step 5 (optional) — Automate screen-view tracking

Most apps don't want to remember to call `logScreenViewEv` in every Activity /
Fragment. The pattern from the reference project:

1. Add a `BaseFragment` (or use a `FragmentLifecycleCallbacks`) that times each
   screen and calls the SDK on `onPause` / `onStop`.
2. Each screen overrides `getScreenName(): String` to return its `ScreenName` constant.

Reference: [`BaseFragmentEv.kt`](../../app/src/main/java/com/tohsoft/weather/ui/base/BaseFragmentEv.kt) (or wherever your project keeps base classes).

---

## Anti-patterns to avoid

| Don't | Why |
|---|---|
| Hard-code screen / button strings at call sites (`"home"`, `"btn_refresh"`) | A typo silently breaks analytics. Use a constant. |
| Use a flat `object ButtonName { const val ... }` instead of enums implementing `ClickBtnEv` | The Lint rule scans enum constants. A flat object bypasses the check. |
| Rename the interface (e.g. `BtnEvent`, `ButtonAction`) | Lint matches by **simple name** `ClickBtnEv`. Renaming = rule silently stops firing. |
| Suppress `ClickBtnEv*` issues with `@Suppress` or a baseline | These rules guard the same convention 200+ entries already follow; suppressing them silently re-introduces lookup drift on the dashboard. Fix the value, don't suppress. |
| Add a project enum entry to the SDK's `AllowPermission` | Permissions vary per project. Define your own enum in `:app` and pass `String` to `logEventAllowPermission` via your wrapper. |
| Put weather-specific event names in the SDK's `models/` package | This is the seam between "common" and "project-specific". Don't blur it. |
| Initialize `AnalyticsModule` from a Fragment's `onCreate` | It must be called once in `Application.onCreate`. Otherwise the lambda providers can capture a destroyed Activity. |
