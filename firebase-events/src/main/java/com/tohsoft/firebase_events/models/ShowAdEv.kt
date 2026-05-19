package com.tohsoft.firebase_events.models

import android.os.Bundle

/**
 * Model for event 'show_ad_ev'
 *
 * Log khi hiển thị ads. User chủ động show ads (ko tính auto refresh)
 * */
data class ShowAdEv(
    /**
     * banner
     * inster
     * app_open
     * reward
     * native
     * */
    private val adType: String,
    /**
     * 0: failed
     * 1: success
     * */
    private val result: String,
) {
    var adId = ""

     fun toBundle(): Bundle {
         val resultValue = if (result == AdResult.SUCCESS) "1" else "0"
         val bundle = Bundle()
         bundle.putString("show_ad_ev_type", adType)
         bundle.putString("show_ad_ev_result", resultValue)
         if (adId.isNotEmpty()) bundle.putString("ad_id", adId)
        return bundle
    }
}
