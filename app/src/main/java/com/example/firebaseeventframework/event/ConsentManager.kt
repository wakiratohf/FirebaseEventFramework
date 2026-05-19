package com.example.firebaseeventframework.event

import android.content.Context
import androidx.core.content.edit
import com.tohsoft.firebase_events.AnalyticsModule

object ConsentManager {

    const val PREFS_NAME = "consent"
    const val KEY_ANALYTICS_CONSENT = "analytics_consent"

    fun isAnalyticsConsented(context: Context, default: Boolean = true): Boolean =
        prefs(context).getBoolean(KEY_ANALYTICS_CONSENT, default)

    fun setAnalyticsConsented(context: Context, consented: Boolean) {
        prefs(context).edit { putBoolean(KEY_ANALYTICS_CONSENT, consented) }
        AnalyticsModule.setEnabled(consented)
    }

    fun onAccept(context: Context) = setAnalyticsConsented(context, true)

    fun onDecline(context: Context) = setAnalyticsConsented(context, false)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
