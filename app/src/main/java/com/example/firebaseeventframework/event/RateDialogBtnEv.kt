package com.example.firebaseeventframework.event

/**
 * Các nút bên trong popup đánh giá ứng dụng (rate dialog). popupName luôn là
 * [PopupName.RATE_DIALOG]; screenName phản ánh màn host nơi dialog hiện lên —
 * giống cách [TaskListBtnEv] gắn nút trong dialog vào screen Tasks.
 *
 * VD `click_btn_ev_name` → `Home_RateDialog_RateNow`, `Settings_RateDialog_RateNow`.
 */
enum class RateDialogBtnEv(
    override val screenName: String,
    override val buttonName: String,
    override val popupName: String,
) : ClickBtnEv {
    // Hiện trên Home khi back để thoát app.
    HOME_RATE_NOW(ScreenName.HOME, "rateNow", PopupName.RATE_DIALOG),
    HOME_RATE_LATER(ScreenName.HOME, "rateLater", PopupName.RATE_DIALOG),
    HOME_DISLIKE(ScreenName.HOME, "dislike", PopupName.RATE_DIALOG),

    // Hiện khi bấm mục "Đánh giá ứng dụng" trong Settings.
    SETTINGS_RATE_NOW(ScreenName.SETTINGS, "rateNow", PopupName.RATE_DIALOG),
    SETTINGS_RATE_LATER(ScreenName.SETTINGS, "rateLater", PopupName.RATE_DIALOG),
    SETTINGS_DISLIKE(ScreenName.SETTINGS, "dislike", PopupName.RATE_DIALOG),
}
