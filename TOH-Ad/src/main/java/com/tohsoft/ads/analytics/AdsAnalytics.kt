package com.tohsoft.ads.analytics

import android.content.Context
import com.google.android.gms.ads.AdValue
import com.tohsoft.ads.wrapper.AdViewWrapper
import com.tohsoft.ads.wrapper.AdWrapperListener
import com.tohsoft.app_event.AdsEventTracker
import com.tohsoft.firebase_events.models.AdResult

/**
 * Cầu nối callback ad của TOH-Ad → [AdsEventTracker] (analytics SDK-agnostic).
 *
 * `:TOH-Ad` là module duy nhất biết SDK ads cụ thể (AdMob). Tracker sống ở
 * `:app-events`, chỉ nhận primitive + [com.tohsoft.firebase_events.models.AdRevenueLike].
 * Phạm vi bridge hiện tại **chỉ banner** (xem [attachBanner]); interstitial /
 * app-open / OPA chưa wire.
 */
object AdsAnalytics {

    /**
     * Khởi tạo tracker. Gọi một lần từ `Application.onCreate()`, **sau**
     * `AnalyticsModule.init` (lần đầu tracker log `ad_engagement_level = 0`).
     *
     * @param isTestMode khi true, đính `ad_id` vào `load_ad_ev`/`show_ad_ev` để debug.
     * @param legacyShowedCount giá trị counter cũ cần migrate (mặc định -1 = integration mới).
     */
    @JvmStatic
    @JvmOverloads
    fun init(context: Context, isTestMode: Boolean, legacyShowedCount: Int = -1) {
        AdsEventTracker.init(context.applicationContext, isTestMode, legacyShowedCount)
    }

    /**
     * Gắn listener analytics vào một banner wrapper và log lần load đầu tiên.
     *
     * Vì `AdViewWrapper` bắt đầu load đồng bộ ngay trong `showBannerBottom`
     * (gọi `notifyAdStartLoad` trước khi listener kịp gắn), nên `load_ad_ev`
     * action `load` của lần đầu được log thủ công ở đây; các lần retry/reload
     * sau đó được forward qua [AdWrapperListener.onAdStartLoad].
     *
     * @return listener đã gắn (để [AdViewWrapper.removeListener] khi dispose).
     */
    fun attachBanner(wrapper: AdViewWrapper, context: Context, screenName: String): AdWrapperListener {
        val listener = bannerListener(context, screenName)
        wrapper.addListener(listener)
        AdsEventTracker.logLoadAd(AdType.BANNER, AdAction.LOAD)
        return listener
    }

    /** Listener forward callback banner → tracker. adType cố định BANNER. */
    private fun bannerListener(context: Context, screenName: String): AdWrapperListener {
        val appContext = context.applicationContext
        return object : AdWrapperListener() {
            // Mốc impression gần nhất để tính duration cho click_ad_ev (giây).
            private var shownAt = 0L

            override fun onAdStartLoad() {
                AdsEventTracker.logLoadAd(AdType.BANNER, AdAction.LOAD)
            }

            override fun onAdLoaded() {
                AdsEventTracker.logLoadAd(AdType.BANNER, AdAction.LOADED)
            }

            override fun onAdFailedToLoad(error: Int, message: String?) {
                AdsEventTracker.logLoadAd(AdType.BANNER, AdAction.LOAD_FAILED)
            }

            override fun onAdImpression() {
                shownAt = System.currentTimeMillis()
                AdsEventTracker.logShowAd(appContext, AdType.BANNER, AdResult.SUCCESS)
            }

            override fun onAdClicked() {
                val duration =
                    if (shownAt == 0L) 0
                    else ((System.currentTimeMillis() - shownAt) / 1000L).toInt()
                AdsEventTracker.logClickAd(AdType.BANNER, screenName, duration)
            }

            override fun onPaidEvent(adValue: AdValue, adUnitId: String?, adSource: String?) {
                AdsEventTracker.logPaidAd(TohAdRevenue(adValue, adUnitId, adSource))
            }
        }
    }
}
