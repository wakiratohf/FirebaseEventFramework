package com.tohsoft.app_event

import com.tohsoft.firebase_events.AnalyticsEvents
import com.tohsoft.firebase_events.models._ShowRateDialogEv

/**
 * Logs `show_rate_dialog_ev` — captures where the rate dialog was shown, how
 * many times it has been shown / how many times the app was opened since
 * install, the stars the user gave, and which button they tapped.
 *
 * Counter-agnostic by design: it takes primitive payloads, never the host
 * app's preference store. The host owns the counters (rate-dialog show count,
 * app-opened count, …) and forwards the resolved values here — this keeps the
 * module copy-pasteable across products. See `docs/RATE_DIALOG_GUIDE.md`.
 */
object RateDialogEventTracker {

    /**
     * Log the `show_rate_dialog_ev` event.
     *
     * @param where screen / condition the dialog was shown under (e.g.
     *   `"home_back_3rd"`).
     * @param showCount number of times the dialog has been shown since install.
     * @param appOpenedCount number of times the app has been opened since install.
     * @param buttonNameClicked button the user tapped — one of `RateUs`, `NoT`,
     *   `Later`, `Good`, `NotG`, `CloseD`, `CloseDByBack`.
     * @param rateStars stars given (1–5); pass `0` when the UI exposes no star value.
     */
    @JvmStatic
    @JvmOverloads
    fun logShowRateDialog(
        where: String,
        showCount: Int,
        appOpenedCount: Int,
        buttonNameClicked: String,
        rateStars: Int = 0,
    ) {
        AnalyticsEvents.logShowRateDialogEv(
            _ShowRateDialogEv(
                where = where,
                showCount = showCount,
                appOpenedCount = appOpenedCount,
                rateStars = rateStars,
                buttonNameClicked = buttonNameClicked,
            )
        )
    }
}
