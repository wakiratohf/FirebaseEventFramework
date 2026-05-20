package com.tohsoft.ads

/**
 * Cấu hình tập trung cho ads — tương tự `AdsConfig` bên app toh-weather:
 * giữ ad unit id ở một chỗ và một kill-switch toàn cục.
 *
 * Mặc định dùng banner test id chính thức của Google (không tính doanh thu,
 * an toàn khi dev). Trước khi release, gán [bannerAdUnitId] = ad unit id thật.
 */
object AdsConfig {

    /**
     * Banner test ad unit id chính thức của Google.
     * https://developers.google.com/admob/android/test-ads
     */
    const val TEST_BANNER_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/9214589741"

    /** Ad unit id dùng để load banner. Thay bằng id thật trước khi phát hành. */
    @Volatile
    @JvmStatic
    var bannerAdUnitId: String = TEST_BANNER_AD_UNIT_ID

    /**
     * Kill-switch toàn cục cho banner. Khi `false`, [BannerAd] không hiển thị gì.
     * Host app có thể nối cờ này với consent / remote config / gói trả phí.
     */
    @Volatile
    @JvmStatic
    var adsEnabled: Boolean = true

    /** Điều kiện duy nhất hiện tại để được phép show ads. */
    fun canShowAds(): Boolean = adsEnabled
}
