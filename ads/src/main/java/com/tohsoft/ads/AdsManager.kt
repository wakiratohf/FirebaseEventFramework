package com.tohsoft.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.tohsoft.app_event.AdsEventTracker

/**
 * Khởi tạo Google Mobile Ads SDK đúng một lần cho cả tiến trình.
 * Gọi từ `Application.onCreate` (xem DemoApp). `MobileAds.initialize` tự
 * chạy phần nặng off-main-thread, nên an toàn để gọi trong onCreate.
 *
 * Cũng khởi tạo [AdsEventTracker] (ad analytics) — phải gọi **sau**
 * `AnalyticsModule.init`, vì lần chạy đầu tracker log user property
 * `ad_engagement_level` mức 0.
 */
object AdsManager {

    @Volatile
    private var initialized = false

    /**
     * @param isTestMode khi true, đính `ad_id` vào `load_ad_ev` / `show_ad_ev`
     *   để debug. Truyền `BuildConfig.DEBUG` hoặc cờ test-ad của project.
     * @param onComplete callback (main thread) khi SDK init xong; có thể null.
     */
    @JvmStatic
    @JvmOverloads
    fun initialize(
        context: Context,
        isTestMode: Boolean = false,
        onComplete: (() -> Unit)? = null,
    ) {
        if (initialized) {
            onComplete?.invoke()
            return
        }
        initialized = true
        AdsEventTracker.init(context.applicationContext, isTestMode)
        MobileAds.initialize(context.applicationContext) {
            onComplete?.invoke()
        }
    }
}
