package com.tohsoft.firebase_events

import android.app.Application
import android.content.Context
import com.google.gson.Gson
import com.tohsoft.firebase_events.models.EventConfigs
import com.tohsoft.firebase_events.utils.FirebasePrefs
import com.tohsoft.firebase_events.utils.TelegramBot
import com.tohsoft.firebase_events.utils.TestLogHelper
import com.tohsoft.firebase_events.utils.WebhookSender

object AnalyticsModule {
    private var appGetter: (() -> Application)? = null
    private var appOpenedTimestampGetter: (() -> Long?)? = null
    private var webhookSender: WebhookSender? = null

    /**
     * Master kill-switch. When false, all `logXxxEv` / user-property calls are
     * dropped early. Use to honour user GDPR/CCPA consent. Default true.
     *
     * Read-only from outside; mutate via [setEnabled] (Java-friendly).
     */
    @Volatile
    var isEnabled: Boolean = true
        private set

    var isTestMode = false

    fun init(
        appProvider: () -> Application,
        sessionProvider: () -> Long?,
        isTestMode: Boolean = false
    ) {
        appGetter = appProvider
        appOpenedTimestampGetter = sessionProvider
        this.isTestMode = isTestMode
        appGetter?.invoke()?.let { context ->
           /* // Load bot info
            val token = FirebasePrefs.getBotToken(context)
            val chatId = FirebasePrefs.getBotChatId(context)
            if (token != null && chatId != null) {
                TelegramBot.initialize(token, chatId)
            }*/
            // Webhook info — only initialize if a transport is registered
            val groupName = FirebasePrefs.getWebhookGroupName(context)
            val userName = FirebasePrefs.getWebhookUserName(context)
            val password = FirebasePrefs.getWebhookPassword(context)
            if (groupName != null && userName != null && password != null) {
                webhookSender?.initialize(groupName, userName, password)
                setLogMode(TestLogMode.WEBHOOK)
            }
            // Load test log mode
            val testMode = FirebasePrefs.getTestLogMode(context)
            TestLogHelper.setTestMode(testMode)
        }
        // Load saved event config
        appGetter?.invoke()?.let { context ->
            val jsonString = FirebasePrefs.getEventConfig(context)
            if (jsonString != null) {
                try {
                    val eventConfigs = Gson().fromJson(jsonString, EventConfigs::class.java)
                    AnalyticsEvents.setEventConfigs(eventConfigs)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setLogMode(mode: TestLogMode) {
        appGetter?.invoke()?.let { context ->
            FirebasePrefs.setTestLogMode(context, mode)
            TestLogHelper.setTestMode(mode)
        }
    }

    fun setBotInfo(context: Context, token: String, chatId: String) {
        TelegramBot.initialize(token, chatId)
        setLogMode(TestLogMode.TELEGRAM)
        FirebasePrefs.setBotToken(context, token)
        FirebasePrefs.setChatId(context, chatId)
    }

    /**
     * Register a custom [WebhookSender] transport (Chatango/Slack/Discord/HTTP
     * etc.). The SDK does not ship a built-in webhook implementation; if not
     * registered, [TestLogMode.WEBHOOK] silently drops events.
     *
     * The actual call to [WebhookSender.initialize] happens in [init] (using
     * persisted credentials) or in [setWebhookInfo] (using freshly-supplied
     * credentials). This setter only attaches the sender so that those flows
     * have a transport to delegate to — calling it multiple times is cheap
     * and does not re-initialize the underlying transport. Same-instance
     * re-registration is treated as a no-op.
     */
    fun setWebhookSender(sender: WebhookSender) {
        if (webhookSender === sender) return
        webhookSender = sender
    }

    fun getWebhookSender(): WebhookSender? = webhookSender

    fun setWebhookInfo(context: Context, groupName: String, userName: String, password: String) {
        webhookSender?.initialize(groupName, userName, password)
        setLogMode(TestLogMode.WEBHOOK)
        FirebasePrefs.setWebhookGroupName(context, groupName)
        FirebasePrefs.setWebhookUserName(context, userName)
        FirebasePrefs.setWebhookPassword(context, password)
    }

    fun setEventConfigs(context: Context, eventConfigs: EventConfigs) {
        AnalyticsEvents.setEventConfigs(eventConfigs)
        // Save as JSON string
        FirebasePrefs.setEventConfig(context, eventConfigs)
    }

    /**
     * Toggle the master kill-switch for analytics. Call with `false` after a
     * user declines GDPR/CCPA consent; call again with `true` if they later
     * opt in. Default is `true`.
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun getApplication(): Application? = appGetter?.invoke()

    fun getAppOpenedTimestamp(): Long? = appOpenedTimestampGetter?.invoke()
}

enum class TestLogMode {
    NONE,
    TELEGRAM,
    WEBHOOK
}