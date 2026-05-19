package com.example.firebaseeventframework

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.example.firebaseeventframework.data.DatabaseProvider
import com.example.firebaseeventframework.event.ConsentManager
import com.tohsoft.firebase_events.AnalyticsModule
import com.tohsoft.firebase_events.AnalyticsUserProperties

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

        val appVersion = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        }.getOrDefault("")

        AnalyticsUserProperties.logLanguageAndAppVersion(
            language = resources.configuration.locales[0].language,
            appVersion = appVersion
        )
    }
}
