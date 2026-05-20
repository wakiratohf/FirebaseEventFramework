package com.example.firebaseeventframework.event

/** Các nút trên màn Settings. `click_btn_ev_name` → `Settings_RateApp`, ... */
enum class SettingsBtnEv(
    override val screenName: String,
    override val buttonName: String,
    override val popupName: String,
) : ClickBtnEv {
    RATE_APP(ScreenName.SETTINGS, "rateApp", PopupName.NONE),
}
