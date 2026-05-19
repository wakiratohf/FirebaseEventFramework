package com.tohsoft.firebase_events.models

import android.os.Bundle

/**
 * Model for event 'load_ad_ev'
 *
 * Log khi tải Ads
 * */
data class LoadAdEv(
    /**
     * banner
     * inster
     * app_open
     * reward
     * native
     * */
    private val adType: String,
    /**
     * load
     * retry
     * loaded
     * load_failed
     * */
    private val action: String,
) {
    var adId = ""

    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString("load_ad_ev_type", adType)
        bundle.putString("load_ad_ev_action", action)
        if (adId.isNotEmpty()) bundle.putString("ad_id", adId)
        return bundle
    }
}
