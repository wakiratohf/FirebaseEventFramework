package com.example.firebaseeventframework.event

import android.content.Context
import androidx.core.content.edit

/**
 * Đếm số lần người dùng mở app kể từ khi cài đặt. Tăng mỗi lần app vào
 * foreground từ trạng thái nền (xem [com.example.firebaseeventframework.DemoApp]).
 *
 * Counter này thuộc về `:app` — `:firebase-events` / `:app-event` cố tình không
 * đọc store của host; host resolve giá trị rồi forward vào `RateDialogEventTracker`
 * (xem `app-event/docs/RATE_DIALOG_GUIDE.md`). Bản gốc toh-weather lưu trong
 * `PreferencesHelper.getAppOpenedCount()`.
 */
object AppOpenCounter {

    private const val PREFS_NAME = "app_open_counter"
    private const val KEY_COUNT = "app_opened_count"

    fun increase(context: Context) {
        prefs(context).edit { putInt(KEY_COUNT, get(context) + 1) }
    }

    fun get(context: Context): Int =
        prefs(context).getInt(KEY_COUNT, 0)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
