package com.example.firebaseeventframework.event

import com.tohsoft.app_event.OpenAppSource

/**
 * Project catalog of launch sources for `open_app_from_ev`
 * (`OAF_ev_{where}_{hour}`). Mirror of the per-app enum described in
 * `app-event/docs/OPEN_APP_FROM_GUIDE.md` step 1.
 *
 * `where` is a wire format: snake_case, lowercase, short, and stable —
 * renaming a value breaks every historical funnel/segment in Firebase.
 */
enum class AppOpenSource(override val where: String) : OpenAppSource {
    /** Default for `Intent.ACTION_MAIN` — user tapped the launcher icon. */
    APP_ICON("app_icon"),

    /** Tagged onto a notification's content `PendingIntent`. */
    NOTIFICATION("notification"),

    /** Tagged onto a home-screen widget's click `PendingIntent`. */
    WIDGET("widget"),

    /** Tagged onto a dynamic app shortcut's intent. */
    SHORTCUT("shortcut"),
}
