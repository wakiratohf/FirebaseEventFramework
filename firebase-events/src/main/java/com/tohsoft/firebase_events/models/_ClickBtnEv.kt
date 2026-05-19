package com.tohsoft.firebase_events.models

import android.os.Bundle
import com.tohsoft.firebase_events.utils.convertSnakeCaseToCamelCase

/**
 * Model for event 'click_btn_ev'
 *
 * - Lấy tất cả các sự kiện khi user click vào các button/chức năng nào đó
 * - Log sự kiện khi user click vào button
 * */
data class _ClickBtnEv(
    private var buttonName: String,
    private var screenName: String,
    private var popupName: String = "",
    /**
     * Thời gian từ khi user mở app tới thời điểm hiện tại, time =  Thời gian hiện tại - Thời gian lúc mở app
     * */
    private val time: Int, // giây
) {
    /**
     * screenName_[popupName]_buttonName
     *
     * Chú ý: Tên sự kiện bao gồm tên màn hình, tên popup (dialog), tên button, nếu không có dialog thì bỏ qua
     * */
    fun getBtnEventNameValue(): String {
        return getScreenWithPopupName() + "_${buttonName.convertSnakeCaseToCamelCase()}"
    }

    fun getScreenWithPopupName(): String {
        val popup: String = if (popupName.isNotEmpty()) "_${popupName.convertSnakeCaseToCamelCase()}" else ""
        return "${screenName.convertSnakeCaseToCamelCase()}${popup}"
    }

    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString("click_btn_ev_name", getBtnEventNameValue())
        bundle.putInt("click_btn_ev_time", time)
        return bundle
    }
}