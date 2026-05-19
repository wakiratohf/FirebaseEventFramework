package com.tohsoft.firebase_events

import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tohsoft.firebase_events.models.AnalyticsEvent as ProjectAnalyticsEvent
import com.tohsoft.firebase_events.models.AppExitEv
import com.tohsoft.firebase_events.models.ClickAdEv
import com.tohsoft.firebase_events.models.EventConfigs
import com.tohsoft.firebase_events.models.IAPEv
import com.tohsoft.firebase_events.models.LoadAdEv
import com.tohsoft.firebase_events.models.OnboardingStepEv
import com.tohsoft.firebase_events.models.PaidAdImpressionEv
import com.tohsoft.firebase_events.models.ShowAdEv
import com.tohsoft.firebase_events.models.TimeOpenAppEv
import com.tohsoft.firebase_events.models._ClickBtnEv
import com.tohsoft.firebase_events.models._OpenAppFromEv
import com.tohsoft.firebase_events.models._ScreenViewEv
import com.tohsoft.firebase_events.models._ShowRateDialogEv
import com.tohsoft.firebase_events.utils.DateTimeHelper
import com.tohsoft.firebase_events.utils.EventNameValidator
import com.tohsoft.firebase_events.utils.TestLogHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AnalyticsEvents {
    private const val TAG = "AnalyticsEvents"
    private const val SCREEN_VIEW_EV = "screen_view_ev"
    private const val CLICK_BTN_EV = "click_btn_ev"
    private const val SHOW_RATE_DIALOG_EV = "show_rate_dialog_ev"
    private const val TIME_OPEN_APP_EV = "time_open_app_ev"
    private const val LOAD_AD_EV = "load_ad_ev"
    private const val SHOW_AD_EV = "show_ad_ev"
    private const val CLICK_AD_EV = "click_ad_ev"
    private const val OPEN_APP_FROM_EV = "open_app_from_ev"
    private const val PAID_AD_IMPRESSION_EV = "paid_ad_impression"
    private const val APP_EXIT_EV = "app_exit"
    private const val ONBOARDING_STEP_EV = "onboarding_step"
    private const val IAP_EV = "iap_ev"

    private var eventConfigs: EventConfigs = EventConfigs()

    fun getConfigs(): EventConfigs {
        return eventConfigs
    }

    fun setEventConfigs(configs: EventConfigs) {
        eventConfigs = configs
    }

    fun logScreenViewEv(screenViewEv: _ScreenViewEv) {
        logEvent(
            eventName = SCREEN_VIEW_EV,
            params = screenViewEv.toBundle(),
            isFirebaseEventsEnable = eventConfigs.screenViewEvEnable
        )
//        AnalyticsUserProperties.logEventScreenOpen(screenViewEv.getScreenNameValue())
    }

    fun logClickBtnEv(clickBtnEv: _ClickBtnEv) {
        logEvent(
            eventName = CLICK_BTN_EV,
            params = clickBtnEv.toBundle(),
            isFirebaseEventsEnable = eventConfigs.clickBtnEvEnable
        )
    }

    fun logShowRateDialogEv(showRateDialogEv: _ShowRateDialogEv) {
        logEvent(
            eventName = SHOW_RATE_DIALOG_EV,
            params = showRateDialogEv.toBundle(),
            isFirebaseEventsEnable = getConfigs().showRateDialogEvEnable
        )
    }

    fun logTimeOpenAppEv(timeOpenAppEv: TimeOpenAppEv) {
        logEvent(
            eventName = TIME_OPEN_APP_EV,
            params = timeOpenAppEv.toBundle(),
            isFirebaseEventsEnable = getConfigs().timeOpenAppEvEnable
        )
    }

    fun logLoadAdEv(loadAdEv: LoadAdEv) {
        logEvent(
            eventName = LOAD_AD_EV,
            params = loadAdEv.toBundle(),
            isFirebaseEventsEnable = getConfigs().loadAdEvEnable
        )
    }

    fun logShowAdEv(showAdEv: ShowAdEv) {
        logEvent(
            eventName = SHOW_AD_EV,
            params = showAdEv.toBundle(),
            isFirebaseEventsEnable = getConfigs().showAdEvEnable
        )
    }

    fun logClickAdEv(clickAdEv: ClickAdEv) {
        logEvent(
            eventName = CLICK_AD_EV,
            params = clickAdEv.toBundle(),
            isFirebaseEventsEnable = getConfigs().clickAdEvEnable
        )
    }

    fun logOpenAppFromEv(from: String) {
        if (from.isEmpty()) return
        val time = DateTimeHelper.currentHour24()
        val openAppFromEv = _OpenAppFromEv(from, time)
        logEvent(
            eventName = OPEN_APP_FROM_EV,
            params = openAppFromEv.toBundle(),
            isFirebaseEventsEnable = getConfigs().openAppFromEvEnable
        )
    }

    fun logOpenAppFromEv(openAppFromEv: _OpenAppFromEv) {
        logEvent(
            eventName = OPEN_APP_FROM_EV,
            params = openAppFromEv.toBundle(),
            isFirebaseEventsEnable = getConfigs().openAppFromEvEnable
        )
    }

    fun logPaidAdImpressionEv(paidAdImpressionEv: PaidAdImpressionEv) {
        logEvent(
            eventName = PAID_AD_IMPRESSION_EV,
            params = paidAdImpressionEv.toBundle(),
            isFirebaseEventsEnable = getConfigs().paidAdImpressionEvEnable
        )
    }

    fun logIAPEv(iapEv: IAPEv) {
        logEvent(
            eventName = IAP_EV,
            params = iapEv.toBundle(),
            isFirebaseEventsEnable = getConfigs().iapEvEnable
        )
    }

    fun logAppExitEv(appExitEv: AppExitEv) {
        logEvent(
            eventName = APP_EXIT_EV,
            params = appExitEv.toBundle(),
            isFirebaseEventsEnable = getConfigs().appExitEvEnable
        )
    }

    fun logOnboardingStepEv(boardingStep: OnboardingStepEv) {
        logEvent(
            eventName = ONBOARDING_STEP_EV,
            params = boardingStep.toBundle(),
            isFirebaseEventsEnable = getConfigs().onboardingStepEvEnable
        )
    }

    /**
     * Generic overload for project-defined events that implement
     * [ProjectAnalyticsEvent]. Honours the master kill-switch and test-log
     * modes the same way as the built-in events.
     */
    fun logEvent(event: ProjectAnalyticsEvent, isFirebaseEventsEnable: Boolean = true) {
        logEvent(event.eventName, event.toBundle(), isFirebaseEventsEnable)
    }

    @JvmStatic
    fun logEvent(eventName: String, params: Bundle?, isFirebaseEventsEnable: Boolean = true) {
        if (!AnalyticsModule.isEnabled) return
        if (AnalyticsModule.isTestMode) EventNameValidator.validate(eventName, params)
        // Radar ko log event & properties
        /*try {
            if (params == null || !isFirebaseEventsEnable) return
            if (AnalyticsModule.isTestMode) {
                val builder = StringBuilder("\n===== FirebaseEvents =====\neventName: $eventName\n---Params---")
                params.keySet().forEach { key ->
                    builder.append("\nKey: ").append(key)
                    val value = params.get(key)
                    builder.append(" | Value: ").append(value.toString())
                }
                builder.append("\n==========================")
                Log.d(TAG, builder.toString())
                TestLogHelper.sendLog(builder.toString())
                return
            }
            // Add to buffer events
            bufferEvents.add(AnalyticsEvent(eventName, params))
            // Check to flush event if buffer size is 5 items
            if (bufferEvents.size >= 5) {
                flushEventsImmediate()
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
        }*/
    }

    fun flushEventsImmediate() {
        try {
            if (bufferEvents.isNotEmpty()) {
                val list = bufferEvents.toList()
                bufferEvents.clear()
                flushEvents(list)
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private val mutex = Mutex()
    private val bufferEvents = mutableListOf<AnalyticsEvent>()
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        FirebaseCrashlytics.getInstance().recordException(throwable)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun flushEvents(list: List<AnalyticsEvent>) {
        // Radar ko log event & properties
        /*GlobalScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            mutex.withLock {
                list.forEach { analyticsEvent ->
                    AnalyticsModule.getApplication()?.let { context ->
                        FirebaseAnalytics.getInstance(context).logEvent(analyticsEvent.eventName, analyticsEvent.params)
                    }
                }
            }
        }*/
    }

    private data class AnalyticsEvent(
        val eventName: String,
        val params: Bundle
    )
}
