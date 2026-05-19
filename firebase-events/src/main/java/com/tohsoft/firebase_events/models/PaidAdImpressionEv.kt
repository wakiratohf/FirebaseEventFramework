package com.tohsoft.firebase_events.models

import android.os.Bundle

/**
 * Model for event 'paid_ad_impression'
 *
 * - Log khi phát sinh sự kiện
 * Check chi tiết trong link: https://developers.google.com/admob/unity/impression-level-ad-revenue
 *
 * Note: receives an [AdRevenueLike] adapter so the SDK does not depend on any
 * specific ads module. Project-side code wraps its concrete revenue payload
 * (AdMob `AdValue`, AppLovin `MaxAd`, etc.) in a class that implements
 * [AdRevenueLike.toBundle].
 * */
data class PaidAdImpressionEv(
    private val adRevenue: AdRevenueLike
) {

    fun toBundle(): Bundle {
        return adRevenue.toBundle()
    }
}
