package com.tohsoft.firebase_events.models

import android.os.Bundle
import com.tohsoft.firebase_events.utils.convertSnakeCaseToCamelCase

/**
 * Model for event 'click_ad_ev'
 *
 * Log khi User click vào ads
 * */
data class ClickAdEv(
    /**
     * banner
     * inster
     * app_open
     * reward
     * native
     * */
    private val adType: String,
    /**
     * screenName_[popupName] - Màn hình hiển thị ads user click
     * */
    private val screenName: String,
    /**
     * giây - Thời gian sau bao lâu kể từ lúc show ad user click vào ads.
     * duration = thời điểm click ad - thời điểm show ad
     * */
    private val duration: Int
) {

    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString("ad_type", adType)
        bundle.putString("screen_name", screenName)
        bundle.putInt("duration", duration)
        return bundle
    }
}
