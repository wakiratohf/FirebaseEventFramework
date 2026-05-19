package com.example.firebaseeventframework.ui.base

import androidx.activity.ComponentActivity
import com.example.firebaseeventframework.event.AnalyticsEventsUtils
import com.example.firebaseeventframework.event.PopupName

abstract class BaseTrackedActivity : ComponentActivity() {

    private var screenStartAt: Long = 0L

    protected abstract fun screenName(): String

    protected open fun popupName(): String = PopupName.NONE

    override fun onResume() {
        super.onResume()
        screenStartAt = System.currentTimeMillis()
        AnalyticsEventsUtils.logScreenStart(screenName(), popupName())
    }

    override fun onPause() {
        super.onPause()
        val duration = ((System.currentTimeMillis() - screenStartAt) / 1000).toInt()
        AnalyticsEventsUtils.logScreenStop(
            screenName = screenName(),
            durationSec = duration,
            popupName = popupName()
        )
    }
}
