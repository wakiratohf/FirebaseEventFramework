package com.tohsoft.firebase_events.models

import com.google.gson.annotations.SerializedName

class EventConfigs() {
    @SerializedName("iap_ev_enable")
    var iapEvEnable: Boolean = true
    @SerializedName("show_rate_dialog_ev_enable")
    var showRateDialogEvEnable: Boolean = true
    @SerializedName("time_open_app_ev_enable")
    var timeOpenAppEvEnable: Boolean = true
    @SerializedName("load_ad_ev_enable")
    var loadAdEvEnable: Boolean = true
    @SerializedName("show_ad_ev_enable")
    var showAdEvEnable: Boolean = true
    @SerializedName("click_ad_ev_enable")
    var clickAdEvEnable: Boolean = true
    @SerializedName("open_app_from_ev_enable")
    var openAppFromEvEnable: Boolean = true
    @SerializedName("screen_view_ev_enable")
    var screenViewEvEnable: Boolean = true
    @SerializedName("click_btn_ev_enable")
    var clickBtnEvEnable: Boolean = true
    @SerializedName("paid_ad_impression_ev_enable")
    var paidAdImpressionEvEnable: Boolean = true
    @SerializedName("app_exit_ev_enable")
    var appExitEvEnable: Boolean = true
    @SerializedName("onboarding_step_enable")
    var onboardingStepEvEnable: Boolean = true
}