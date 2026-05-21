package com.example.firebaseeventframework.event

/** Các nút trên màn Subscription. `click_btn_ev_name` → `Subscription_subscribe`, ... */
enum class SubscriptionBtnEv(
    override val screenName: String,
    override val buttonName: String,
    override val popupName: String,
) : ClickBtnEv {
    SUBSCRIBE(ScreenName.SUBSCRIPTION, "subscribe", PopupName.NONE),
    RESTORE(ScreenName.SUBSCRIPTION, "restorePurchases", PopupName.NONE),
    CLOSE(ScreenName.SUBSCRIPTION, "close", PopupName.NONE),
    RESTORE_DIALOG_CONFIRM(ScreenName.SUBSCRIPTION, "restore", PopupName.RESTORE_DIALOG),
    RESTORE_DIALOG_CANCEL(ScreenName.SUBSCRIPTION, "cancel", PopupName.RESTORE_DIALOG),
}
