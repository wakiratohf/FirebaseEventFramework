package com.example.firebaseeventframework.event

/**
 * Các nút trên màn Tasks, gồm cả nút trong 2 dialog (popupName != "").
 * VD `click_btn_ev_name` → `Tasks_AddTask`, `Tasks_AddTaskDialog_Confirm`.
 */
enum class TaskListBtnEv(
    override val screenName: String,
    override val buttonName: String,
    override val popupName: String,
) : ClickBtnEv {
    ADD_TASK(ScreenName.TASKS, "addTask", PopupName.NONE),
    TOGGLE_DONE(ScreenName.TASKS, "toggleDone", PopupName.NONE),
    OPEN_TIMER(ScreenName.TASKS, "openTimer", PopupName.NONE),
    DELETE(ScreenName.TASKS, "delete", PopupName.NONE),
    ADD_CONFIRM(ScreenName.TASKS, "confirm", PopupName.ADD_TASK_DIALOG),
    ADD_CANCEL(ScreenName.TASKS, "cancel", PopupName.ADD_TASK_DIALOG),
    DELETE_CONFIRM(ScreenName.TASKS, "confirm", PopupName.DELETE_TASK_DIALOG),
    DELETE_CANCEL(ScreenName.TASKS, "cancel", PopupName.DELETE_TASK_DIALOG),
}
