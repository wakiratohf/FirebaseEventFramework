package com.tohsoft.firebase_events.models

import android.os.Bundle

/**
 * Model for event 'app_exit'
 *
 * - Log khi app xuống background hoặc bị kill
 * */
data class AppExitEv(
    /** Tính theo giây từ khi mở app */
    private val duration: Int,
    /**
     * Tên màn hình cuối cùng trước khi user thoát,
     * để tránh nhiễu thì khi user từ màn hình khác về Home thì không log lại tên màn hình Home (Màn hình Home chỉ log lần đầu khi mở app)
     * Có tính tên dialog khi từ các dialog xin quyền trở về
     * */
    private val lastActiveScreen: String // screen_name

) {

    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt("duration_sec", duration)
        bundle.putString("last_active_screen", lastActiveScreen)
        return bundle
    }
}
