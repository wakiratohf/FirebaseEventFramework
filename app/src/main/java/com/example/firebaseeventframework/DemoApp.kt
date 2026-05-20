package com.example.firebaseeventframework

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.core.os.ConfigurationCompat
import com.example.firebaseeventframework.data.DatabaseProvider
import com.example.firebaseeventframework.event.ConsentManager
import com.example.firebaseeventframework.event.ScreenName
import com.tohsoft.app_event.AppEventsInstaller
import com.tohsoft.firebase_events.AnalyticsModule
import com.tohsoft.firebase_events.AnalyticsUserProperties
import com.tohsoft.firebase_events.utils.FirebasePrefs

class DemoApp : Application() {

    @Volatile
    private var foregroundedAt: Long = 0L
    private var startedActivities: Int = 0

    override fun onCreate() {
        super.onCreate()

        DatabaseProvider.init(this)

        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        AnalyticsModule.init(
            appProvider = { this },
            sessionProvider = {
                if (foregroundedAt == 0L) null
                else System.currentTimeMillis() - foregroundedAt
            },
            isTestMode = isDebug
        )

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities == 0) {
                    foregroundedAt = System.currentTimeMillis()
                    FirebasePrefs.saveAppOpenedTimestamp(this@DemoApp, foregroundedAt)
                }
                startedActivities++
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities <= 0) {
                    startedActivities = 0
                    foregroundedAt = 0L
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        val consented = getSharedPreferences(ConsentManager.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(ConsentManager.KEY_ANALYTICS_CONSENT, true)
        AnalyticsModule.setEnabled(consented)

        // Map mỗi Activity sang ScreenName catalog để last_active_screen của
        // app_exit khớp với screen_view_ev (app-event/docs/INTEGRATION.md Step 5).
        AppEventsInstaller.install(this) { activity ->
            when (activity) {
                is MainActivity -> ScreenName.HOME
                is TaskListActivity -> ScreenName.TASKS
                is TimerActivity -> ScreenName.TIMER
                is StatsActivity -> ScreenName.STATS
                is SettingsActivity -> ScreenName.SETTINGS
                else -> activity.javaClass.simpleName
            }
        }

        val appVersion = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        }.getOrDefault("")

        AnalyticsUserProperties.logLanguageAndAppVersion(
            language = ConfigurationCompat.getLocales(resources.configuration)[0]?.language ?: "",
            appVersion = appVersion
        )
    }
}
