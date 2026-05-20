package com.example.firebaseeventframework.event

/** Các nút trên màn Home. `click_btn_ev_name` → `Home_OpenTasks`, ... */
enum class HomeBtnEv(
    override val screenName: String,
    override val buttonName: String,
    override val popupName: String,
) : ClickBtnEv {
    OPEN_TASKS(ScreenName.HOME, "openTasks", PopupName.NONE),
    OPEN_TIMER(ScreenName.HOME, "openTimer", PopupName.NONE),
    OPEN_STATS(ScreenName.HOME, "openStats", PopupName.NONE),
}
