package com.tohsoft.app_event

import android.content.Context
import com.tohsoft.firebase_events.AnalyticsEvents
import com.tohsoft.firebase_events.AnalyticsUserProperties
import com.tohsoft.firebase_events.models.AdRevenueLike
import com.tohsoft.firebase_events.models.ClickAdEv
import com.tohsoft.firebase_events.models.LoadAdEv
import com.tohsoft.firebase_events.models.PaidAdImpressionEv
import com.tohsoft.firebase_events.models.ShowAdEv
import com.tohsoft.firebase_events.utils.FirebasePrefs

/**
 * Logs the ad analytics events — `load_ad_ev`, `show_ad_ev`, `click_ad_ev`,
 * `paid_ad_impression` — plus the `ad_engagement_level` user property (a
 * monotonic count of ads shown, persisted via [FirebasePrefs]).
 *
 * Ads-SDK-agnostic by design: it takes primitive payloads and an
 * [AdRevenueLike] adapter, never a concrete mediation type. The host app owns
 * the bridge from its ads SDK (e.g. registering an ad-events listener on the
 * mediation SDK) and forwards each callback here — see
 * `docs/ADS_EVENT_GUIDE.md`. This keeps both `:firebase-events` and this module
 * free of any dependency on a specific ads SDK.
 */
object AdsEventTracker {

    @Volatile
    private var testMode = false

    /**
     * Initialize the tracker. Call once from `Application.onCreate()`.
     *
     * @param isTestMode when true, the optional `ad_id` payload is attached to
     *   `load_ad_ev` / `show_ad_ev` for debugging. Pass `BuildConfig.DEBUG ||
     *   BuildConfig.TEST_AD` (or your project's equivalent).
     * @param legacyShowedCount value of a pre-existing ad-showed counter to
     *   migrate into [FirebasePrefs] on the first run after adopting this
     *   tracker. Pass the count from your old store so users who already
     *   crossed engagement thresholds don't re-log them. Omit (`-1`) for fresh
     *   integrations — the tracker then starts at 0 and logs level 0.
     */
    @JvmStatic
    @JvmOverloads
    fun init(context: Context, isTestMode: Boolean, legacyShowedCount: Int = -1) {
        testMode = isTestMode
        if (FirebasePrefs.getAdShowedCount(context) < 0) {
            if (legacyShowedCount >= 0) {
                // Migrate silently — these thresholds were already logged before.
                FirebasePrefs.setAdShowedCount(context, legacyShowedCount)
            } else {
                FirebasePrefs.setAdShowedCount(context, 0)
                AnalyticsUserProperties.logAdEngagementLevel(0)
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun logLoadAd(adType: String, action: String, adId: String? = null) {
        AnalyticsEvents.logLoadAdEv(
            LoadAdEv(adType = adType, action = action).apply {
                if (testMode) this.adId = adId.orEmpty()
            }
        )
    }

    /**
     * Logs `show_ad_ev` and bumps the `ad_engagement_level` user property.
     */
    @JvmStatic
    @JvmOverloads
    fun logShowAd(context: Context, adType: String, result: String, adId: String? = null) {
        AnalyticsEvents.logShowAdEv(
            ShowAdEv(adType = adType, result = result).apply {
                if (testMode) this.adId = adId.orEmpty()
            }
        )
        bumpAdEngagement(context)
    }

    @JvmStatic
    fun logClickAd(adType: String, screenName: String, duration: Int) {
        AnalyticsEvents.logClickAdEv(
            ClickAdEv(adType = adType, screenName = screenName, duration = duration)
        )
    }

    @JvmStatic
    fun logPaidAd(adRevenue: AdRevenueLike) {
        AnalyticsEvents.logPaidAdImpressionEv(PaidAdImpressionEv(adRevenue = adRevenue))
    }

    private fun bumpAdEngagement(context: Context) {
        val count = FirebasePrefs.getAdShowedCount(context) + 1
        FirebasePrefs.setAdShowedCount(context, count)
        AnalyticsUserProperties.logAdEngagementLevel(count)
    }
}
