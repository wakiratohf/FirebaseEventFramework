package com.tohsoft.ads.wrapper

import com.google.android.gms.ads.AdValue

abstract class AdWrapperListener {
    open fun onAdStartLoad() {}
    open fun onAdLoaded() {}
    open fun onAdFailedToLoad(error: Int, message: String?) {}
    open fun onAdClicked() {}
    open fun onAdOpened() {}
    open fun onAdClosed() {}

    /** Ad ghi nhận một impression (banner: AdMob onAdImpression). Dùng cho analytics show_ad_ev. */
    open fun onAdImpression() {}

    /** Doanh thu impression-level (AdMob OnPaidEventListener). Dùng cho analytics paid_ad_impression. */
    open fun onPaidEvent(adValue: AdValue, adUnitId: String?, adSource: String?) {}
}
