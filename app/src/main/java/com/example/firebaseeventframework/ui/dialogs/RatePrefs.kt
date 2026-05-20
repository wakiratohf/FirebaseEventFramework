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
    private const val KEY_SHOW_COUNT = "PREF_RATE_DIALOG_SHOW_COUNT"

    private const val SHOW_THRESHOLD = 2
    private const val NEVER_SHOW = 6

    /**
     * Lần bấm back-thoát thứ mấy thì dialog hiện ra. count bắt đầu từ 0 và tăng
     * mỗi lần back; tới khi count == [SHOW_THRESHOLD] mới hiện → đó là lần bấm
     * thứ `SHOW_THRESHOLD + 1`. Dùng để dựng param `where` (`home_back_<n>rd`).
     */
    val showOnBackPressOrdinal: Int get() = SHOW_THRESHOLD + 1

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

    /**
     * Tăng tổng số lần dialog đã được hiển thị (từ lúc cài đặt). Tương đương
     * `PreferencesHelper.increaseRateDialogShowCount()` bản gốc — gọi mỗi khi
     * dialog hiện ra, để forward vào `show_rate_dialog_ev`.
     */
    fun increaseShowCount(context: Context) {
        prefs(context).edit { putInt(KEY_SHOW_COUNT, getShowCount(context) + 1) }
    }

    fun getShowCount(context: Context): Int =
        prefs(context).getInt(KEY_SHOW_COUNT, 0)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
