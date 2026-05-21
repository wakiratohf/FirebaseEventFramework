package com.tohsoft.app_event

import android.os.SystemClock
import com.tohsoft.firebase_events.AnalyticsEvents
import com.tohsoft.firebase_events.AnalyticsUserProperties
import com.tohsoft.firebase_events.models._ScreenViewEv

class DialogScreenViewEv(
    private val screenName: String?,
    private val popupName: String,
) {
    private var openTimestamp = 0L
    private var closed = true

    fun onShow() {
        openTimestamp = SystemClock.elapsedRealtime()
        if (closed && !screenName.isNullOrEmpty()) {
            AnalyticsUserProperties.logEventScreenOpen(screenName, popupName)
        }
        closed = false
    }

    fun onClosed() {
        if (openTimestamp == 0L) return
        closed = true
        val name = screenName ?: return
        val durationSec = ((SystemClock.elapsedRealtime() - openTimestamp) / 1000).toInt()
        openTimestamp = 0L
        if (durationSec > 0) {
            AnalyticsEvents.logScreenViewEv(
                _ScreenViewEv(name, _ScreenViewEv.State.STOP, popupName, durationSec)
            )
        }
    }
}
