package com.tohsoft.firebase_events.models

import android.os.Bundle

/**
 * Model for event 'open_app_from_ev'
 *
 * - Xác định xem user mở app từ đâu và thời gian nào trong ngày
 * - Log sự kiện khi user mở app
 * */
data class _OpenAppFromEv(
    /** where là vị trí user chọn mở app */
    private val where: String,
    /** time (0-24h), user mở app giờ nào tính theo giờ đó của máy */
    private val time: Int
) {
    /**
     * OAF_ev_(where)_(time)
     * */
    fun getOpenAppFromValue(): String {
        return "OAF_ev_${where}_${time}"
    }

    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString("open_app_from_ev_where_time", getOpenAppFromValue())
        return bundle
    }
}
