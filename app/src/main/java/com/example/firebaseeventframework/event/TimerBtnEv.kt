package com.example.firebaseeventframework.event

/** Các nút điều khiển trên màn Timer. VD `click_btn_ev_name` → `Timer_Start`. */
enum class TimerBtnEv(
    override val screenName: String,
    override val buttonName: String,
    override val popupName: String,
) : ClickBtnEv {
    START(ScreenName.TIMER, "start", PopupName.NONE),
    PAUSE(ScreenName.TIMER, "pause", PopupName.NONE),
    RESET(ScreenName.TIMER, "reset", PopupName.NONE),
    SKIP(ScreenName.TIMER, "skip", PopupName.NONE),
    CHANGE_SESSION_TYPE(ScreenName.TIMER, "changeSessionType", PopupName.NONE),
    MARK_TASK_DONE(ScreenName.TIMER, "markTaskDone", PopupName.NONE),
}
