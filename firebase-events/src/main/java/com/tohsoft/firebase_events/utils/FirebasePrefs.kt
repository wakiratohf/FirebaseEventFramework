package com.tohsoft.firebase_events.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.tohsoft.firebase_events.TestLogMode
import com.tohsoft.firebase_events.models.EventConfigs

object FirebasePrefs {

    private const val PREF_NAME = "firebase_prefs"
    private const val KEY_APP_OPENED_TIMESTAMP = "app_opened_timestamp"
    private const val KEY_LAST_TIME_OPEN_APP = "last_time_open_app"
    private const val KEY_AD_SHOWED_COUNT = "ad_showed_count"
    private const val KEY_USER_TIER = "user_tier"
    private const val KEY_SUBSCRIPTION_STATUS = "subscription_status"
    private const val KEY_COUNTRY = "country"
    private const val KEY_EVENT_CONFIG = "event_config"
    private const val KEY_TEST_LOG_MODE = "test_log_mode"
    private const val KEY_ALLOW_PERMISSION = "allow_permission"
    private const val KEY_BOT_TOKEN = "bot_token"
    private const val KEY_CHAT_ID = "chat_id"

    private const val KEY_WEBHOOK_GROUP_NAME = "webhook_group_name"
    private const val KEY_WEBHOOK_USER_NAME = "webhook_user_name"
    private const val KEY_WEBHOOK_PASSWORD = "webhook_password"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
    * Country
    * */
    @JvmStatic
    fun getCountry(context: Context): String {
        return getPrefs(context).getString(KEY_COUNTRY, "") ?: ""
    }

    fun setCountry(context: Context, country: String) {
        getPrefs(context).edit { putString(KEY_COUNTRY, country) }
    }

    /**
    * Subscription status
    * */
    fun getSubscriptionStatus(context: Context): String {
        return getPrefs(context).getString(KEY_SUBSCRIPTION_STATUS, "") ?: ""
    }

    fun setSubscriptionStatus(context: Context, status: String) {
        getPrefs(context).edit { putString(KEY_SUBSCRIPTION_STATUS, status) }
    }

    /**
    * User tier
    * */
    fun getCurrentUserTier(context: Context): String {
        return getPrefs(context).getString(KEY_USER_TIER, "") ?: ""
    }

    fun serUserTier(context: Context, tier: String) {
        getPrefs(context).edit { putString(KEY_USER_TIER, tier) }
    }

    fun setTestLogMode(context: Context, logMode: TestLogMode) {
        getPrefs(context).edit { putString(KEY_TEST_LOG_MODE, logMode.toString()) }
    }

    fun getTestLogMode(context: Context): TestLogMode {
        val modeStr = getPrefs(context).getString(KEY_TEST_LOG_MODE, TestLogMode.NONE.toString())
            ?: TestLogMode.NONE.toString()
        return TestLogMode.valueOf(modeStr)
    }

    fun setWebhookGroupName(context: Context, groupName: String) {
        getPrefs(context).edit { putString(KEY_WEBHOOK_GROUP_NAME, groupName) }
    }

    fun getWebhookGroupName(context: Context): String? {
        return getPrefs(context).getString(KEY_WEBHOOK_GROUP_NAME, null)
    }

    fun setWebhookUserName(context: Context, userName: String) {
        getPrefs(context).edit { putString(KEY_WEBHOOK_USER_NAME, userName) }
    }

    fun getWebhookUserName(context: Context): String? {
        return getPrefs(context).getString(KEY_WEBHOOK_USER_NAME, null)
    }

    fun setWebhookPassword(context: Context, password: String) {
        getPrefs(context).edit { putString(KEY_WEBHOOK_PASSWORD, password) }
    }

    fun getWebhookPassword(context: Context): String? {
        return getPrefs(context).getString(KEY_WEBHOOK_PASSWORD, null)
    }

    fun setBotToken(context: Context, token: String) {
        getPrefs(context).edit { putString(KEY_BOT_TOKEN, token) }
    }

    fun getBotToken(context: Context): String? {
        return getPrefs(context).getString(KEY_BOT_TOKEN, null)
    }

    fun setChatId(context: Context, chatId: String) {
        getPrefs(context).edit { putString(KEY_CHAT_ID, chatId) }
    }

    fun getBotChatId(context: Context): String? {
        return getPrefs(context).getString(KEY_CHAT_ID, null)
    }

    fun setEventConfig(context: Context, eventConfigs: EventConfigs) {
        getPrefs(context).edit { putString(KEY_EVENT_CONFIG, Gson().toJson(eventConfigs)) }
    }

    fun getEventConfig(context: Context): String? {
        return getPrefs(context).getString(KEY_EVENT_CONFIG, null)
    }

    fun saveAppOpenedTimestamp(context: Context, timestamp: Long) {
        getPrefs(context).edit { putLong(KEY_APP_OPENED_TIMESTAMP, timestamp) }
    }

    fun getAppOpenedTimestamp(context: Context): Long? {
        val value = getPrefs(context).getLong(KEY_APP_OPENED_TIMESTAMP, -1L)
        return if (value != -1L) value else null
    }

    fun saveLastTimeOpenApp(context: Context, timestamp: Long) {
        getPrefs(context).edit { putLong(KEY_LAST_TIME_OPEN_APP, timestamp) }
    }

    fun getLastTimeOpenApp(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_TIME_OPEN_APP, 0L)
    }

    /**
     * Monotonic count of ads shown — backs the `ad_engagement_level` user
     * property. Returns -1 when never set (caller seeds it on first run).
     */
    fun getAdShowedCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_AD_SHOWED_COUNT, -1)
    }

    fun setAdShowedCount(context: Context, count: Int) {
        getPrefs(context).edit { putInt(KEY_AD_SHOWED_COUNT, count) }
    }

    fun getAllowPermission(context: Context): String {
        return getPrefs(context).getString(KEY_ALLOW_PERMISSION, "") ?: ""
    }

    fun setAllowPermission(context: Context, perssion: String){
        return getPrefs(context).edit {
            putString(KEY_ALLOW_PERMISSION, perssion)
        }
    }

    fun clear(context: Context) {
        getPrefs(context).edit() { clear() }
    }
}