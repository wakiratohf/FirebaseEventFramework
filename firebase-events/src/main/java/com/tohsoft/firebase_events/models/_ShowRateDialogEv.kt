package com.tohsoft.firebase_events.models

import android.os.Bundle

/**
 * Model for event 'show_rate_dialog_ev'
 *
 * - Hiển thị rate dialog
 * - Log khi user click vào button nào  đó
 * */
data class _ShowRateDialogEv(
    private val where: String, // Ở màn hình nào
    private val showCount: Int, // 1, 2... Số lần dialog được show
    private val appOpenedCount: Int, // 1, 2, 3... User mở app lần thứ n
    private var rateStars: Int = 0, // 1, 2, 3, 4, 5 : User rate 1-5 sao (Nếu UI ko có giá trị rate thì đặt = 0)
    /**
     * RateUs
     * NoT
     * Later
     * Good
     * NotG
     * CloseD
     * CloseDByBack
     * */
    private val buttonNameClicked: String
) {

    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString("where", where)
        bundle.putInt("nums_dlg_show_since_install", showCount)
        bundle.putInt("nums_app_open_since_install", appOpenedCount)
        bundle.putInt("rate_stars", rateStars)
        bundle.putString("button_click", buttonNameClicked)
        return bundle
    }
}
