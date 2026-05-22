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
from each rate-dialog button handler. The general shape:

```kotlin
import com.tohsoft.app_event.RateDialogEventTracker

fun onRateDialogButtonClicked(buttonName: String, rateStars: Int = 0) {
    RateDialogEventTracker.logShowRateDialog(
        where = "home_back_3rd",
        showCount = /* dialog show count from your store */,
        appOpenedCount = /* app-opened count from your store */,
        buttonNameClicked = buttonName,
        rateStars = rateStars, // 0 when the dialog has no star input
    )
}
```

### How this app wires it (Compose reference)

This demo app implements the dialog in Compose and keeps both counters in
`:app`:

| Concern | Owner in `:app` |
|---|---|
| Dialog show count (`showCount`) | [`ui/dialogs/RatePrefs.kt`](../../app/src/main/java/com/example/firebaseeventframework/ui/dialogs/RatePrefs.kt) — `increaseShowCount()` / `getShowCount()` |
| App-opened count (`appOpenedCount`) | [`event/AppOpenCounter.kt`](../../app/src/main/java/com/example/firebaseeventframework/event/AppOpenCounter.kt) — `increase()` called from `DemoApp` on each foreground / `get()` |
| Dialog UI + glue | [`ui/dialogs/RateDialog.kt`](../../app/src/main/java/com/example/firebaseeventframework/ui/dialogs/RateDialog.kt) — `RateDialog` composable + `logRateDialogButtonEv(...)` |
| Hosts that show the dialog | `MainActivity` (back-to-exit on Home), `SettingsActivity` (Settings entry) |

The thin glue that resolves counters and forwards to the tracker
(`logRateDialogButtonEv` in `RateDialog.kt`):

```kotlin
fun logRateDialogButtonEv(
    context: Context,
    where: String,
    buttonNameClicked: String,
    rateStars: Int = 0,
) {
    RateDialogEventTracker.logShowRateDialog(
        where = where,
        showCount = RatePrefs.getShowCount(context),
        appOpenedCount = AppOpenCounter.get(context),
        buttonNameClicked = buttonNameClicked,
        rateStars = rateStars,
    )
}
```

`showCount` is bumped via `RatePrefs.increaseShowCount(...)` the moment the
dialog appears (in the host's `DisposableEffect`), so the button event carries
the post-increment count.

Call sites map each button to its name + star value:

| Button | `buttonNameClicked`           | `rateStars` |
|---|-------------------------------|---|
| Đánh giá 5 sao | `RateUs` (`RATE_BTN_RATE_US`) | `5` — the button is an explicit 5-star action |
| Để sau | `Later` (`RATE_BTN_LATER`)    | `0` |
| Không thích | `Not` (`RATE_BTN_DISLIKE`)    | `0` |

`where` per host:
- **Home (back-to-exit):** `whereHomeBack()` → `"home_back_${RatePrefs.showOnBackPressOrdinal}rd"`,
  i.e. the back-press number at which the dialog fires (threshold + 1). With the
  default threshold it resolves to `"home_back_3rd"` and tracks the threshold if
  it changes.
- **Settings:** `WHERE_SETTINGS` = `"settings"` (not gated by the back-press counter).

> Original production reference (`toh-weather`): wraps this in
> `AppUtil.logShowRateDialogEv(activity, buttonName)`, called from each button
> handler in `RateDialog.kt` — the `where`/`showCount`/`appOpenedCount` values
> are read from the app's `PreferencesHelper` there.

---

## Verify

In test mode (`isTestMode = true` passed to `AnalyticsModule.init`), events are
dumped to Logcat with tag `AnalyticsEvents`. Otherwise, with Firebase DebugView
enabled, trigger the dialog and tap a button — expect `show_rate_dialog_ev` with
the five params above. Logging is gated by the `show_rate_dialog_ev_enable`
remote-config flag (default `true`).

Two trigger points in this app:
- **Home:** press back to exit `RatePrefs.showOnBackPressOrdinal` times (default
  3rd press) → `where = "home_back_3rd"`.
- **Settings:** tap "Đánh giá ứng dụng" → `where = "settings"`.

Tapping "Đánh giá 5 sao" emits `button_click = "RateUs"` with `rate_stars = 5`;
"Để sau" / "Không thích" emit `Later` / `NoT` with `rate_stars = 0`.

---

## Reference

- API: [`RateDialogEventTracker`](../src/main/java/com/tohsoft/app_event/RateDialogEventTracker.kt).
- Firebase log layer:
  [`AnalyticsEvents.logShowRateDialogEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsEvents.kt),
  [`_ShowRateDialogEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/models/_ShowRateDialogEv.kt).
- Remote-config toggle: `show_rate_dialog_ev_enable` in `EventConfigs` (default `true`).
- App-side (this repo):
  [`RateDialog.kt`](../../app/src/main/java/com/example/firebaseeventframework/ui/dialogs/RateDialog.kt) (UI + `logRateDialogButtonEv`),
  [`RatePrefs.kt`](../../app/src/main/java/com/example/firebaseeventframework/ui/dialogs/RatePrefs.kt) (show count + threshold),
  [`AppOpenCounter.kt`](../../app/src/main/java/com/example/firebaseeventframework/event/AppOpenCounter.kt) (app-opened count),
  call sites in `MainActivity` / `SettingsActivity`.
