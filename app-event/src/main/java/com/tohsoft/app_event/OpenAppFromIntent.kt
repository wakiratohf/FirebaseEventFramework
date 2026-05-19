package com.tohsoft.app_event

import android.content.Intent
import com.tohsoft.firebase_events.AnalyticsEvents

/**
 * Drop-in helper for the `open_app_from_ev` flow: standardises the intent
 * extras key used to carry the launch source, and dispatches the log call
 * from the receiving Activity.
 *
 * Typical wiring:
 *
 * 1. Producer side (notification, widget, shortcut) — when building the
 *    `PendingIntent` that opens the launcher Activity, call
 *    [putSource] to tag the intent with the source the user came from.
 *
 * 2. Consumer side (launcher Activity) — call [logFromIntent] from both
 *    `onCreate(savedInstanceState)` (with `getIntent()`) and
 *    `onNewIntent(intent)`. The helper itself decides whether to log.
 *
 * See `docs/OPEN_APP_FROM_GUIDE.md` for the full integration recipe.
 */
object OpenAppFromIntent {
    /**
     * Stable key under which the source string is stored in the launch
     * intent. Exposed so producers and consumers in different modules can
     * reference the same constant without redefining it.
     */
    const val EXTRA_OPEN_FROM = "extra_open_from"

    /**
     * Tag [intent] with the launch source so the receiving Activity can
     * log it via [logFromIntent].
     *
     * Returns the same intent for fluent chaining.
     */
    @JvmStatic
    fun putSource(intent: Intent, source: OpenAppSource): Intent {
        intent.putExtra(EXTRA_OPEN_FROM, source.where)
        return intent
    }

    /**
     * Inspect a launch intent and log `open_app_from_ev` if the intent
     * unambiguously identifies a launch source.
     *
     * Rules:
     * - `intent == null` → no-op.
     * - `intent.action == Intent.ACTION_MAIN` → log [defaultActionMainSource]
     *   (typically your `APP_ICON` entry — the user tapped the launcher).
     * - intent carries [EXTRA_OPEN_FROM] → log that string (lowercased).
     * - otherwise → no-op (system intents, configuration changes, etc.).
     *
     * Safe to call from both `onCreate` and `onNewIntent`. Wraps the log
     * call in [runCatching] so a malformed extras bundle cannot crash the
     * launcher Activity.
     */
    @JvmStatic
    fun logFromIntent(intent: Intent?, defaultActionMainSource: OpenAppSource) {
        intent ?: return
        when {
            intent.action == Intent.ACTION_MAIN -> {
                AnalyticsEvents.logOpenAppFromEv(defaultActionMainSource.where)
            }
            intent.hasExtra(EXTRA_OPEN_FROM) -> {
                intent.getStringExtra(EXTRA_OPEN_FROM)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { from ->
                        runCatching { AnalyticsEvents.logOpenAppFromEv(from.lowercase()) }
                    }
            }
        }
    }
}
