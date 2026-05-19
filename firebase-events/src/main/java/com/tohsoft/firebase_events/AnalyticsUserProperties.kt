package com.tohsoft.firebase_events

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.tohsoft.firebase_events.AnalyticsUserProperties.adEngagementLevels
import com.tohsoft.firebase_events.models.AllowPermission
import com.tohsoft.firebase_events.models.UserPropertyEv
import com.tohsoft.firebase_events.utils.FirebasePrefs
import com.tohsoft.firebase_events.utils.TestLogHelper

object AnalyticsUserProperties {
    private const val TAG = "AnalyticsUserProperties"
//    private const val DAYS_SINCE_INSTALL_EV = "days_since_install"
//    private const val USER_OPEN_APP_IN_SESSION = "user_open_app_in_session_n"
    private const val SCREEN_OPEN_EV = "screen_open"
    private const val ALLOW_PERMISSION_EV = "allow_permission"
    private const val ALLOW_NOTIFICATION_EV = "allow_notification"
    private const val USER_TIER = "user_tier"
    private const val SUBSCRIPTION_STATUS = "subscription_status"
    private const val APP_VERSION = "app_version"
    private const val LANGUAGE = "language"
    private const val COUNTRY = "country"
    private const val HAS_SEEN_TUTORIAL_EV = "has_seen_tutorial"
    private const val AD_ENGAGEMENT_LEVEL = "ad_engagement_level"

    private val adEngagementLevels = listOf(0, 1, 5, 10, 20, 50, 100, 200, 500)

    /**
     * Log Ad Engagement Level
     * Chỉ set khi đạt các ngưỡng trong yêu cầu [adEngagementLevels], lớn hơn 500 thì không log nữa
     * */
    fun logAdEngagementLevel(level: Int) {
        if (level !in adEngagementLevels || level > 500) return
        logUserPropertyEv(
            UserPropertyEv(
                userPropertyName = AD_ENGAGEMENT_LEVEL,
                property = level.toString()
            )
        )
    }

    /**
     * Log các MH onboarding mà user đã xem
     * */
    @JvmStatic
    fun logHasSeenTutorial(hasSeenTutorials: String) {
        logUserPropertyEv(
            UserPropertyEv(
                userPropertyName = HAS_SEEN_TUTORIAL_EV,
                property = hasSeenTutorials
            )
        )
    }

    /**
     * Log Country khi lần đầu mở app
     * */
    @JvmStatic
    fun logCountry(context: Context, country: String) {
        if (FirebasePrefs.getCountry(context).isEmpty() && country.isNotEmpty()) {
            FirebasePrefs.setCountry(context, country)
            logUserPropertyEv(
                UserPropertyEv(
                    userPropertyName = COUNTRY,
                    property = country
                )
            )
        }
    }

    /**
     * Log App Language and App Version khi mở app
     * */
    fun logLanguageAndAppVersion(language: String, appVersion: String) {
        logUserPropertyEv(
            UserPropertyEv(
                userPropertyName = LANGUAGE,
                property = language
            )
        )
        logUserPropertyEv(
            UserPropertyEv(
                userPropertyName = APP_VERSION,
                property = appVersion
            )
        )
    }

    /**
     * Lần đầu log free, khi user chuyển trạng thái thì log theo
     * Free / Premium / Trial
     * */
    fun logUserTier(context: Context, tier: String) {
        if (tier != FirebasePrefs.getCurrentUserTier(context)) {
            FirebasePrefs.serUserTier(context, tier)
            logUserPropertyEv(
                UserPropertyEv(
                    userPropertyName = USER_TIER,
                    property = tier
                )
            )
        }
    }

    /**
     * Log Subscription status
     * "none", "trial", "active", "expired", "lifetime", hoặc tên từng gói cụ thể
     *
     * -> AppLock chưa có IAP nên mặc định không cần gọi nhiều
     * */
    fun logSubscriptionStatus(context: Context, status: String) {
        if (status != FirebasePrefs.getSubscriptionStatus(context)) {
            FirebasePrefs.setSubscriptionStatus(context, status)
            logUserPropertyEv(
                UserPropertyEv(
                    userPropertyName = SUBSCRIPTION_STATUS,
                    property = status
                )
            )
        }
    }

    /** PhongNX: Sếp Hà báo không cần log properties này nữa (29/04/2026) */
    fun logUserOpenAppInSession(sessionNumber: Int) {
        /*logUserPropertyEv(
            UserPropertyEv(
                userPropertyName = USER_OPEN_APP_IN_SESSION,
                property = "session_$sessionNumber"
            )
        )*/
    }

    /** PhongNX: Sếp Hà báo không cần log properties này nữa (29/04/2026) */
    fun logEventDaysSinceInstall(days: Int) {
        /*logUserPropertyEv(
            UserPropertyEv(
                userPropertyName = DAYS_SINCE_INSTALL_EV,
                property = "day_$days"
            )
        )*/
    }

    @JvmOverloads
    @JvmStatic
    fun logEventScreenOpen(screenName: String, popupName: String = "") {
        if (screenName.isEmpty()) return
        logUserPropertyEv(
            UserPropertyEv(
                userPropertyName = SCREEN_OPEN_EV,
                property = getScreenOpenName(screenName, popupName)
            )
        )
        /*CategoryScreen.findCategoryOfScreen(screenName)?.let {
            onScreenVisited(it)
        }*/
    }

    @JvmStatic
    fun logEventAllowPermission(permission: AllowPermission, isGranted: Boolean) {
        val ctx = AnalyticsModule.getApplication() ?: return
        val allowedPermissions = FirebasePrefs.getAllowPermission(ctx)
        val currentSet = allowedPermissions.toCharArray().toMutableSet()
        val key = permission.identify.first()
        if (isGranted) {
            currentSet.add(key)
        } else {
            currentSet.remove(key)
        }
        val newPermissionString = currentSet.joinToString(separator = "")
        if (newPermissionString == allowedPermissions) {
            return
        }

        FirebasePrefs.setAllowPermission(ctx, newPermissionString)
        if (newPermissionString.isBlank()) return
        logUserPropertyEv(
            UserPropertyEv(
                userPropertyName = ALLOW_PERMISSION_EV,
                property = newPermissionString
            )
        )
    }

    private fun getScreenOpenName(screenName: String, popupName: String = ""): String {
        val popup: String = if (popupName.isNotEmpty()) "_$popupName" else ""
        return "sr_${screenName}${popup}"
    }

    /**
    * noti,notiFullScreen
    * */
    fun logEventAllowNotification(isNormalNotifyGranted: Boolean, isFullScreenNotifyGranted: Boolean) {
        var allowedNotifications = ""
        if (isNormalNotifyGranted) {
            allowedNotifications = "noti"
        }
        if (isFullScreenNotifyGranted) {
            if (allowedNotifications.isNotEmpty()) allowedNotifications += ","
            allowedNotifications += "notiFullScreen"
        }

        if (allowedNotifications.isEmpty()) return

        logUserPropertyEv(
            UserPropertyEv(
                userPropertyName = ALLOW_NOTIFICATION_EV,
                property = allowedNotifications
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun logUserPropertyEv(userPropertyEv: UserPropertyEv) {
        if (!AnalyticsModule.isEnabled) return
        // Radar ko log event & properties
        /*if (AnalyticsModule.isTestMode) {
            val builder = StringBuilder("\n===== User Properties =====")
            builder.append("\nName: ").append(userPropertyEv.userPropertyName)
            builder.append("\nProperty: ").append(userPropertyEv.property)
            builder.append("\n==========================")
            Log.d(TAG, builder.toString())
            TestLogHelper.sendLog(builder.toString())
            return
        }

        AnalyticsModule.getApplication()?.let { context ->
            FirebaseAnalytics.getInstance(context).setUserProperty(userPropertyEv.userPropertyName, userPropertyEv.property)
        }*/
    }
}