package com.example.firebaseeventframework.ui.dialogs

import android.content.Context
import androidx.core.content.edit

/**
 * Logic đếm điều kiện hiển thị rate dialog, port nguyên từ app toh-weather
 * (`RateDialog.PREF_RATE_DIALOG_COUNT`).
 *
 * - count < [SHOW_THRESHOLD] : chưa hiện, chỉ tăng đếm số lần back-thoát.
 * - count >= [NEVER_SHOW]    : không bao giờ hiện lại (đã rate / đã dislike).
 * - ở giữa                   : đủ điều kiện hiện dialog.
 */
object RatePrefs {

    private const val PREFS_NAME = "rate_dialog"
    private const val KEY_COUNT = "PREF_RATE_DIALOG_COUNT"

    private const val SHOW_THRESHOLD = 2
    private const val NEVER_SHOW = 6

    /**
     * Gọi khi người dùng bấm back để thoát app. Trả về true nếu nên hiển thị
     * dialog ngay lúc này; nếu chưa đủ điều kiện thì tự tăng bộ đếm và trả false.
     */
    fun shouldShowOnExit(context: Context): Boolean {
        val count = prefs(context).getInt(KEY_COUNT, 0)
        if (count >= NEVER_SHOW) return false
        if (count < SHOW_THRESHOLD) {
            prefs(context).edit { putInt(KEY_COUNT, count + 1) }
            return false
        }
        return true
    }

    /** Người dùng đã rate hoặc dislike → không hiện lại nữa. */
    fun setNeverShowAgain(context: Context) =
        prefs(context).edit { putInt(KEY_COUNT, NEVER_SHOW) }

    /** Người dùng chọn "Để sau" → reset bộ đếm về 0. */
    fun resetCount(context: Context) =
        prefs(context).edit { putInt(KEY_COUNT, 0) }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
