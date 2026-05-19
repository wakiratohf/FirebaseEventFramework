package com.tohsoft.firebase_events.models

import android.os.Bundle
import com.tohsoft.firebase_events.utils.convertSnakeCaseToCamelCase

/**
 * Model for event 'screen_view_ev'
 *
 * - Kiểm tra xem user vào màn hình nào và ở đó bao lâu
 * - Log sự kiện khi user thoát khỏi màn hình nào đó (có tính xuống bg)
 * */
data class _ScreenViewEv(
    private var screenName: String,
    /**
     * Trạng thái của MH khi log, là một trong các giá trị sau:
     * - overlap: bị màn hình khác đè lên nhưng chưa thoát
     * - stop: thoát hoàn toàn khỏi màn hình
     * - home_recent: app xuống background
     * */
    private var screenState: State,
    /**
     * Popup đang hiển thị trên MH (nếu có)
     * */
    private var popupName: String = "",
    /**
     * Thời gian user ở lại màn hình, duration = Thời gian hiện tại - Thời gian lúc user mở màn hình
     * */
    private var duration: Int // giây
) {
    /**
     * screenName_[popupName]
     *
     * Tên màn hình hoặc popup hiện tại. Nếu không có popup (dialog) thì bỏ qua
     * Tên màn hình hiện tại. Nếu màn hình đó đang có dialog thì thêm dialog name vào cuối
     * */
    fun getScreenNameValue(): String {
        val popup: String = if (popupName.isNotEmpty()) "_${popupName.convertSnakeCaseToCamelCase()}" else ""
        return "${screenName.convertSnakeCaseToCamelCase()}${popup}"
    }

    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString("screen_name", getScreenNameValue())
        bundle.putString("screen_state", screenState.toString().lowercase())
        bundle.putInt("duration", duration)
        return bundle
    }

    enum class State {
        OVERLAP,
        STOP,
        HOME_RECENT
    }

}