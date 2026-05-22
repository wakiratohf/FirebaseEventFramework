# Event catalog

Every built-in event and user property the SDK ships with, plus its bundle schema.

> Firebase limits to keep in mind:
> - Event name: ≤ 40 chars, `[A-Za-z][A-Za-z0-9_]*`
> - Param key: ≤ 40 chars
> - Param string value: ≤ 100 chars
> The [`EventNameValidator`](../src/main/java/com/tohsoft/firebase_events/utils/EventNameValidator.kt) warns (doesn't throw) on violations when `AnalyticsModule.isTestMode == true`.

---

## Events

### `screen_view_ev`

User entered or left a screen.

| Param | Type | Source | Notes |
|---|---|---|---|
| `screen_name` | string | `_ScreenViewEv.getScreenNameValue()` | `screenName` (CamelCase) `+ _popupName` if popup is showing |
| `screen_state` | string | `_ScreenViewEv.State` | `overlap` / `stop` / `home_recent` |
| `duration` | int | caller | Seconds the user spent on that screen |

```kotlin
AnalyticsEvents.logScreenViewEv(
    _ScreenViewEv(
        screenName = "home",
        screenState = _ScreenViewEv.State.STOP,
        popupName = "",
        duration = 12
    )
)
```

---

### `click_btn_ev`

User tapped a button.

| Param | Type | Notes |
|---|---|---|
| `click_btn_ev_name` | string | `screenName_popupName_buttonName` (CamelCase, popup may be empty) |
| `click_btn_ev_time` | int | Seconds since the app was foregrounded |

---

### `show_rate_dialog_ev`

A rate-us dialog appeared, or the user closed one.

| Param | Type | Notes |
|---|---|---|
| `where` | string | Screen showing the dialog |
| `nums_dlg_show_since_install` | int | How many times the dialog has been shown to this user, ever |
| `nums_app_open_since_install` | int | How many times the user has opened the app |
| `rate_stars` | int | 1–5 if rated, 0 if dismissed |
| `button_click` | string | `RateUs` / `NoT` / `Later` / `Good` / `NotG` / `CloseD` / `CloseDByBack` |

---

### `time_open_app_ev`

User opened the app (cold start or resume).

| Param | Type | Notes |
|---|---|---|
| `time_open_app_ev_current_in_hour` | string | Current hour (0–23) in device local time |
| `time_open_app_ev_last_in_hour` | string | Hour of the previous open |
| `time_open_app_ev_delta` | string | Minutes since the previous open |

---

### `open_app_from_ev`

Where the app was opened from (launcher, notification, deep link, widget, ...).

| Param | Type | Notes |
|---|---|---|
| `open_app_from_ev_where_time` | string | `OAF_ev_{where}_{hour}` |

Has a convenience overload that takes just the `from: String` and computes the hour for you.

---

### `load_ad_ev`

Ad request lifecycle event.

| Param | Type | Notes |
|---|---|---|
| `load_ad_ev_type` | string | `banner` / `inter` / `app_open` / `reward` / `native` |
| `load_ad_ev_action` | string | `load` / `retry` / `loaded` / `load_failed` |
| `ad_id` | string | Optional; only included when set on the model |

---

### `show_ad_ev`

App actively showed an ad to the user (auto-refresh impressions don't count).

| Param | Type | Notes |
|---|---|---|
| `show_ad_ev_type` | string | banner / inter / app_open / reward / native |
| `show_ad_ev_result` | string | `1` for success ([`AdResult.SUCCESS`](../src/main/java/com/tohsoft/firebase_events/models/AdResult.kt)), `0` otherwise |
| `ad_id` | string | Optional |

---

### `click_ad_ev`

User tapped on an ad.

| Param | Type | Notes |
|---|---|---|
| `ad_type` | string | banner / inter / app_open / reward / native |
| `screen_name` | string | Screen the ad was on |
| `duration` | int | Seconds between show and click |

---

### `paid_ad_impression`

A revenue-bearing ad impression. The bundle is whatever your `AdRevenueLike`
implementation returns from `toBundle()` — see [`AdRevenueLike.kt`](../src/main/java/com/tohsoft/firebase_events/models/AdRevenueLike.kt).

In this repo the adapter is `TohAdRevenue` (`:TOH-Ad`, package
`com.tohsoft.ads.analytics`), whose keys are: `value` (double, currency units),
`currency`, `precision` (`estimated`/`publisher_provided`/`precise`/`unknown`),
`ad_platform` (`admob`), plus `ad_unit_id` and `ad_source` when present.

---

### `iap_ev`

In-app purchase attempt.

| Param | Type | Notes |
|---|---|---|
| `iap_ev_where` | string | `open_app` / `onboarding` / `{screen}_pro_icon` / `{screen}_unlock_{feature}` |
| `iap_ev_result` | string | `1` if successful (including trial), `0` otherwise |
| `iap_is_trial` | string | `1` if this was a free-trial start, `0` otherwise |
| `ipa_package_name` | string | The product SKU |

---

### `app_exit`

App went to background or was killed.

| Param | Type | Notes |
|---|---|---|
| `duration_sec` | int | Seconds since the app was foregrounded |
| `last_active_screen` | string | The last screen the user was on |

---

### `onboarding_step`

Progress through the onboarding/tutorial flow.

| Param | Type | Notes |
|---|---|---|
| `step_name` | string | Stable identifier for the step |
| `completed` | string | `true` / `false` (or your own marker) |
| `reason` | string | Free-text; e.g. "user_denied_camera_permission" |

---

## User properties

User properties are long-lived. Set them with `AnalyticsUserProperties.xxx()`.

| Property name | Setter | When to call |
|---|---|---|
| `screen_open` | `logEventScreenOpen(screenName, popupName)` | Each time a screen opens (lets you segment by "currently on screen X") |
| `allow_permission` | `logEventAllowPermission(permission, isGranted)` | Each time a permission state changes |
| `allow_notification` | `logEventAllowNotification(isNormal, isFullScreen)` | After querying notification grant state |
| `user_tier` | `logUserTier(context, tier)` | When the tier changes; values: `Free` / `Premium` / `Trial` |
| `subscription_status` | `logSubscriptionStatus(context, status)` | When the subscription state changes; values: `none` / `trial` / `active` / `expired` / `lifetime` / SKU |
| `app_version` | `logLanguageAndAppVersion(language, appVersion)` | On every app open (cheap to call repeatedly) |
| `language` | `logLanguageAndAppVersion(language, appVersion)` | Same call as above |
| `country` | `logCountry(context, country)` | Once on first launch (no-op on subsequent calls) |
| `has_seen_tutorial` | `logHasSeenTutorial(hasSeenTutorials)` | After completing each tutorial step; you decide the encoding |
| `ad_engagement_level` | `logAdEngagementLevel(level)` | Each ad click — the SDK filters to threshold values {0,1,5,10,20,50,100,200,500} only |

### `AllowPermission` enum values

| Enum | Identify char | Title |
|---|---|---|
| `ALARM` | `A` | AlarmPermission |
| `BATTERY_OPTIMIZATION` | `B` | BatteryPermission |
| `FULL_SCREEN_NOTIFICATION` | `F` | FullScreenNotificationPermission |
| `LOCATION` | `L` | LocationPermission |
| `POST_NOTIFICATION` | `N` | NotificationPermission |
| `OVERLAY_PERMISSION` | `O` | OverlayPermission |
| `LOCATION_BACKGROUND` | `G` | BackgroundLocationPermission |

The user property `allow_permission` stores the granted set as a concatenation
of identify chars (e.g. `"ALN"` means alarm + location + notification granted).

---

## Sanity-check tooling

- Logcat tag `AnalyticsValidator` — soft warnings for name/value length violations (debug builds only).
- Logcat tag `AnalyticsEvents` — TestLogMode dumps the full event payload.
- Firebase Console → Analytics → DebugView — see events live for devices marked with `adb shell setprop debug.firebase.analytics.app <appId>`.
- Firebase Console → Analytics → BigQuery export — for offline analysis.
