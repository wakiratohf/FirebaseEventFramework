package com.tohsoft.firebase_events.utils

import android.util.Log
import com.tohsoft.firebase_events.AnalyticsModule
import com.tohsoft.firebase_events.TestLogMode

object TestLogHelper {
    private var testMode: TestLogMode = TestLogMode.NONE
    private val eventList = mutableListOf<String>()

    fun setTestMode(testLogMode: TestLogMode) {
        testMode = testLogMode
    }

    fun sendLog(message: String) {
        try {
            if (testMode == TestLogMode.NONE) return
            if (testMode == TestLogMode.TELEGRAM) {
                eventList.add(message)
                if (eventList.size >= 5) {
                    val builderMessage = StringBuilder()
                    eventList.forEach { eventStr ->
                        builderMessage.append(eventStr).append("\n")
                    }
                    eventList.clear()
                    Thread { TelegramBot.sendMessage(builderMessage.toString()) }.start()
                }
            } else {
                AnalyticsModule.getWebhookSender()?.sendEvent(message)
            }
        } catch (e: Exception) {
            Log.e("TestLogHelper", "Error sending test log", e)
        }
    }
}