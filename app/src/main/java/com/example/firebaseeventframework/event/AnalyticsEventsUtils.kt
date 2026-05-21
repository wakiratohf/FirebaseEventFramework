package com.example.firebaseeventframework.event

import com.tohsoft.firebase_events.AnalyticsEvents
import com.tohsoft.firebase_events.AnalyticsModule
import com.tohsoft.firebase_events.AnalyticsUserProperties
import com.tohsoft.firebase_events.models.AnalyticsEvent
import com.tohsoft.firebase_events.models.IAPEv
import com.tohsoft.firebase_events.models._ClickBtnEv
import com.tohsoft.firebase_events.models._ScreenViewEv
import com.tohsoft.firebase_events.models._ScreenViewEv.State

object AnalyticsEventsUtils {

    fun logScreenStart(screenName: String, popupName: String = PopupName.NONE) {
        AnalyticsUserProperties.logEventScreenOpen(screenName, popupName)
    }

    fun logScreenStop(
        screenName: String,
        durationSec: Int,
        popupName: String = PopupName.NONE,
        state: State = State.STOP
    ) {
        AnalyticsEvents.logScreenViewEv(
            _ScreenViewEv(
                screenName = screenName,
                screenState = state,
                popupName = popupName,
                duration = durationSec
            )
        )
    }

    fun logClickBtn(
        screenName: String,
        buttonName: String,
        popupName: String = PopupName.NONE
    ) {
        AnalyticsEvents.logClickBtnEv(
            _ClickBtnEv(
                screenName = screenName,
                buttonName = buttonName,
                popupName = popupName,
                time = secondsSinceAppOpen()
            )
        )
    }

    /**
     * Overload nên dùng tại call-site: truyền enum [ClickBtnEv] thay vì 3 String
     * rời, để convention drift bị bắt ngay tại chỗ khai báo enum (bởi Lint),
     * không phải ở từng nơi gọi.
     */
    fun logClickBtn(event: ClickBtnEv) {
        logClickBtn(event.screenName, event.buttonName, event.popupName)
    }

    fun logProjectEvent(event: AnalyticsEvent) {
        AnalyticsEvents.logEvent(event)
    }

    /**
     * Log event mua hàng (in-app purchase). Gom lời gọi SDK [IAPEv] về một chỗ
     * thay vì để call-site tự dựng model, đồng nhất với các wrapper khác.
     */
    fun logIap(
        where: String,
        paymentSuccess: Boolean,
        isTrial: Boolean,
        productId: String,
    ) {
        AnalyticsEvents.logIAPEv(
            IAPEv(
                where = where,
                paymentSuccess = paymentSuccess,
                isTrial = isTrial,
                productId = productId,
            )
        )
    }

    private fun secondsSinceAppOpen(): Int {
        val openedAt = AnalyticsModule.getAppOpenedTimestamp() ?: return 0
        return ((System.currentTimeMillis() - openedAt) / 1000).toInt()
    }
}
