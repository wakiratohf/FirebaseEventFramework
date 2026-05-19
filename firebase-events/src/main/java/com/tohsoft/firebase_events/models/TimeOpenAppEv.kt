package com.tohsoft.firebase_events.models

import android.os.Bundle

/**
 * Model for event 'time_open_app_ev'
 *
 * Log khi user mở app (Tính cả khi user mở app từ BG)
 * */
data class TimeOpenAppEv(
    /** Giờ hiện tại theo máy khi user mở app, tính theo khung giờ 24h trong 1 ngày của quốc gia đó */
    private val currentLocalTimeInHour: String,
    /** Giờ gần nhất khi user mở app. Tính theo khung giờ 24h trong 1 ngày của quốc gia đó. */
    private val lastTimeOpenAppInHour: String,
    /** Thời gian giữa lần mở app hiện tại và lần gần nhất (phút) */
    private val openAppIntervalInMinute: String
) {

    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString("time_open_app_ev_current_in_hour", currentLocalTimeInHour)
        bundle.putString("time_open_app_ev_last_in_hour", lastTimeOpenAppInHour)
        bundle.putString("time_open_app_ev_delta", openAppIntervalInMinute)
        return bundle
    }
}
