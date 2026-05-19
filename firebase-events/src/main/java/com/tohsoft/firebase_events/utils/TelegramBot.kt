package com.tohsoft.firebase_events.utils

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object TelegramBot {
    private val TAG = "TelegramBot"
    private var botToken = ""
    private var chatId = ""

    fun initialize(token: String, chatId: String) {
        this.botToken = token
        this.chatId = chatId
    }

    fun sendMessage(messageText: String) {
        try {
            if (chatId.isEmpty() || botToken.isEmpty() || messageText.isEmpty()) return

            val urlString = "https://api.telegram.org/bot$botToken/sendMessage"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true

            // JSON payload
            val jsonInputString = """{"chat_id":"$chatId","text":"$messageText"}"""

            // Gửi dữ liệu
            conn.outputStream.use { os ->
                val input = jsonInputString.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            // Đọc phản hồi
            val response = StringBuilder()
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    response.append(line!!.trim())
                }
            }

//            Log.d(TAG, "Response from Telegram: $response")
        } catch (e: Exception) {
            Log.e(TAG, e.message?:"Error sending message to Telegram")
        }
    }
}