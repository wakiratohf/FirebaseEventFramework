# show_rate_dialog_ev — Integration guide

Logs the **rate dialog** — where it was shown, how many times it has been
shown / how many times the app was opened since install, the stars the user
gave, and which button they tapped.

> Module split:
> - `:firebase-events` owns the Firebase log + payload formatter:
>   [`AnalyticsEvents.logShowRateDialogEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsEvents.kt),
>   [`_ShowRateDialogEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/models/_ShowRateDialogEv.kt),
>   the `show_rate_dialog_ev_enable` remote-config flag in `EventConfigs`.
> - `:app-event` (this module) owns [`RateDialogEventTracker`](../src/main/java/com/tohsoft/app_event/RateDialogEventTracker.kt):
>   it takes primitive payloads and forwards them to the log layer.
> - **`:app`** owns the rate-dialog UI and the counters. Neither
>   `:firebase-events` nor `:app-event` reads the host's preference store — the
>   host resolves the counts and forwards them into the tracker.

---

## Why the counters stay in `:app`

`RateDialogEventTracker` deliberately takes primitives (`where`, `showCount`,
`appOpenedCount`, `buttonNameClicked`, `rateStars`) — never a `PreferencesHelper`
or any app-specific store. That keeps this module copy-pasteable across products
that persist these counts differently. The thin glue that reads the counters
lives in `:app`.

---

## What gets logged

Event name: `show_rate_dialog_ev`

| Param | Type | Example | Notes |
|---|---|---|---|
| `where` | string | `"home_back_3rd"` | Screen / condition the dialog was shown under. |
| `nums_dlg_show_since_install` | int | `2` | Times the dialog has been shown since install. |
| `nums_app_open_since_install` | int | `17` | Times the app has been opened since install. |
| `rate_stars` | int | `0`–`5` | Stars given; `0` when the UI exposes no star value. |
| `button_click` | string | `"RateUs"` | One of `RateUs`, `NoT`, `Later`, `Good`, `NotG`, `CloseD`, `CloseDByBack`. |

---

## Integration

In `:app`, resolve the counters from your store, then forward to the tracker
from each rate-dialog button handler:

```kotlin
import com.tohsoft.app_event.RateDialogEventTracker

fun onRateDialogButtonClicked(buttonName: String) {
    RateDialogEventTracker.logShowRateDialog(
        where = "home_back_3rd",
        showCount = prefs.getRateDialogShowCount(),
        appOpenedCount = prefs.getAppOpenedCount(),
        buttonNameClicked = buttonName,
        // rateStars defaults to 0 when the dialog has no star input
    )
}
```

If your dialog collects a star rating, pass it explicitly:

```kotlin
RateDialogEventTracker.logShowRateDialog(
    where = "home_back_3rd",
    showCount = prefs.getRateDialogShowCount(),
    appOpenedCount = prefs.getAppOpenedCount(),
    buttonNameClicked = "Good",
    rateStars = 5,
)
```

> Production reference (`toh-weather`): wraps this in
> `AppUtil.logShowRateDialogEv(activity, buttonName)`, called from each button
> handler in `RateDialog.kt` — the `where`/`showCount`/`appOpenedCount` values
> are read from the app's `PreferencesHelper` there.

---

## Verify

In test mode (`isTestMode = true` passed to `AnalyticsModule.init`), events are
dumped to Logcat with tag `AnalyticsEvents`. Otherwise, with Firebase DebugView
enabled, trigger the dialog and tap a button — expect `show_rate_dialog_ev` with
the five params above. Logging is gated by the `show_rate_dialog_ev_enable`
remote-config flag.

---

## Reference

- API: [`RateDialogEventTracker`](../src/main/java/com/tohsoft/app_event/RateDialogEventTracker.kt).
- Firebase log layer:
  [`AnalyticsEvents.logShowRateDialogEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsEvents.kt),
  [`_ShowRateDialogEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/models/_ShowRateDialogEv.kt).
- Remote-config toggle: `show_rate_dialog_ev_enable` in `EventConfigs`.
