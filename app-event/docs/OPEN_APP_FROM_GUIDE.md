# open_app_from_ev — Integration guide

This guide walks through wiring up the `open_app_from_ev` event end-to-end in a
fresh project. It is the canonical pattern — same convention used by
`toh-weather` and any other product that copies this module.

> Module split:
> - `:firebase-events` owns the **Firebase log** layer
>   (`AnalyticsEvents.logOpenAppFromEv(...)`, `_OpenAppFromEv` formatter,
>   remote-config toggle).
> - `:app-event` (this module) owns the **app-level Android plumbing**
>   (intent extras key, `PendingIntent` builder, launcher Activity dispatcher,
>   the `OpenAppSource` interface).
>
> What stays in your `:app`:
> - Your own enum listing every launch source you care about.
> - Wiring `OpenAppFromIntent.putSource(...)` into your notification / widget
>   `PendingIntent` builders.
> - Calling `OpenAppFromIntent.logFromIntent(...)` from your launcher Activity.

---

## 1. Define your enum

Each app has a different surface area (weather has 17 sources, a music player
might have 5, a banking app has deep links). The module does **not** ship a
generic enum — define yours alongside your domain code:

```kotlin
// app/src/main/java/com/example/myapp/analytics/MyAppOpenSource.kt
package com.example.myapp.analytics

import com.tohsoft.app_event.OpenAppSource

enum class MyAppOpenSource(override val where: String) : OpenAppSource {
    APP_ICON("app_icon"),
    NOTI_DAILY("noti_daily"),
    NOTI_REALTIME("noti_realtime"),
    WIDGET("widget"),
    LOCK_SCREEN("lock_screen"),
    DEEP_LINK("deep_link"),
    SHORTCUT("shortcut"),
}
```

Conventions for `where`:
- snake_case, lowercase.
- Short enough to stay readable inside `OAF_ev_{where}_{hour}` (the full param
  value is capped at 100 chars by Firebase).
- Stable — changing the spelling breaks every historical funnel/segment in
  Firebase. Treat it as a wire format.

---

## 2. Tag launch intents (producer side)

Anywhere you build a `PendingIntent` that opens the launcher Activity —
notifications, widgets, shortcuts, foreground-service taps — tag the intent
with the source:

```kotlin
import com.tohsoft.app_event.OpenAppFromIntent

fun openAppPendingIntent(
    context: Context,
    source: MyAppOpenSource,
): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    OpenAppFromIntent.putSource(intent, source)
    return PendingIntent.getActivity(
        context,
        /* requestCode = */ source.ordinal,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
```

`putSource` only writes the `EXTRA_OPEN_FROM` extra. You stay in control of
everything else (action, flags, request code, address/payload extras).

Typical producer sites:

| Surface | Where to call `putSource` |
|---|---|
| Notification (regular) | Inside `NotificationCompat.Builder` setup, on the content intent. |
| Notification full-screen | On both the content intent and the full-screen intent (different sources if you want to distinguish). |
| Widget | In `RemoteViews.setOnClickPendingIntent`. |
| App shortcut (dynamic) | On the shortcut's intent. |
| FCM push | In `FirebaseMessagingService.onMessageReceived`. |

---

## 3. Dispatch from the launcher Activity (consumer side)

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OpenAppFromIntent.logFromIntent(intent, MyAppOpenSource.APP_ICON)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        OpenAppFromIntent.logFromIntent(intent, MyAppOpenSource.APP_ICON)
    }
}
```

The second argument is the source to log when the intent has no
`EXTRA_OPEN_FROM` and its action is `Intent.ACTION_MAIN` — i.e. the user opened
the app from the launcher icon. Use your `APP_ICON` enum value here.

Behaviour summary:

| Incoming intent | What gets logged |
|---|---|
| `action == ACTION_MAIN`, no extras | `OAF_ev_app_icon_<hour>` |
| Has `EXTRA_OPEN_FROM = "noti_daily"` | `OAF_ev_noti_daily_<hour>` |
| Has `EXTRA_OPEN_FROM = ""` | nothing (defensive) |
| Has `EXTRA_OPEN_FROM = "RANDOM"` | `OAF_ev_random_<hour>` (lowercased; module never throws) |
| Empty/system intent (no action, no extras) | nothing |

---

## 4. Lifecycle: when does this fire?

| Scenario | Fires? | Notes |
|---|---|---|
| Cold start from launcher icon | ✅ `app_icon` | `onCreate` runs with `ACTION_MAIN`. |
| Cold start from notification | ✅ source from extras | `onCreate` runs with your tagged intent. |
| Warm start from Recent apps (user pressed Home, returned via task switcher) | ❌ | Neither `onCreate` nor `onNewIntent` runs again. |
| Notification tapped while app is foregrounded | ✅ source from extras | `onNewIntent` runs. |
| Notification tapped while task is alive in background | ✅ source from extras | `onNewIntent` runs (provided `singleTop`/`singleTask` is configured). |
| Configuration change (rotation) | ❌ | Activity is recreated but no new launch intent — `ACTION_MAIN` won't be present. |

If you also need to track "the user came back to the app" regardless of how —
log `time_open_app_ev` from `:firebase-events` on
`ProcessLifecycleOwner.ON_START`. Don't reuse `open_app_from_ev` for warm
starts; it would inflate the funnel counts.

---

## 5. Common pitfalls

**Forgetting `onNewIntent`.** If your launcher Activity is `singleTop` /
`singleTask`, tapping a notification while the task is alive routes to
`onNewIntent` — `onCreate` won't run. Both handlers must call
`logFromIntent`.

**Using `getIntent()` after `setIntent(newIntent)`.** Inside `onNewIntent`,
either pass the parameter directly (recommended), or call
`setIntent(newIntent)` first if you want `getIntent()` to return it later.

**Stale `PendingIntent`s.** If you reuse the same `requestCode` for different
sources, Android may keep the old extras. Pass `FLAG_UPDATE_CURRENT` (as in the
example above) or use distinct request codes per source.

**Different action per address.** If you set a distinct `action` on each
`PendingIntent` (e.g. `"address_42"`) so Android updates extras correctly,
that's fine — `OpenAppFromIntent.logFromIntent` only checks for
`ACTION_MAIN` specifically; any other action falls through to the
`EXTRA_OPEN_FROM` check.

**Source string capitalisation.** The dispatcher lowercases the extra before
logging, so `EXTRA_OPEN_FROM = "Noti_Daily"` and `"noti_daily"` produce
identical events. Still, store the values lowercase in your enum to avoid
confusion in code review.

---

## 6. Migrating an existing project that already logs `open_app_from_ev`

If you previously rolled your own `EXTRA_OPEN_FROM` constant and dispatch
function (the pattern this guide grew out of), the migration is two lines:

1. Replace your local constant with `OpenAppFromIntent.EXTRA_OPEN_FROM`
   (same string, `"extra_open_from"`, so existing `PendingIntent`s keep
   working during rollout).
2. Replace your dispatch function body with
   `OpenAppFromIntent.logFromIntent(intent, YourEnum.APP_ICON)`.

The on-the-wire param (`open_app_from_ev_where_time = OAF_ev_{where}_{hour}`)
is unchanged, so Firebase dashboards stay valid.

---

## 7. Reference

- API (this module):
  [`OpenAppFromIntent`](../src/main/java/com/tohsoft/app_event/OpenAppFromIntent.kt),
  [`OpenAppSource`](../src/main/java/com/tohsoft/app_event/OpenAppSource.kt).
- Firebase log layer (`:firebase-events`):
  `_OpenAppFromEv`, `AnalyticsEvents.logOpenAppFromEv`,
  schema in `firebase-events/docs/EVENT_CATALOG.md`.
- Remote-config toggle: `open_app_from_ev_enable` in `EventConfigs` (lives in
  `:firebase-events`).
