package com.example.firebaseeventframework.ui.base

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.firebaseeventframework.event.AnalyticsEventsUtils
import com.example.firebaseeventframework.event.PopupName

abstract class BaseTrackedActivity : ComponentActivity() {

    private var screenStartAt: Long = 0L

    protected abstract fun screenName(): String

    protected open fun popupName(): String = PopupName.NONE

    /**
     * Bật immersive fullscreen (ẩn status bar + navigation bar) cho màn này.
     * Mặc định `true` cho toàn app; subclass override trả `false` nếu cần giữ
     * system bars (ví dụ màn có form nhập liệu dài).
     */
    protected open fun immersiveEnabled(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (immersiveEnabled()) applyImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // System bars có thể hiện lại sau dialog/bàn phím hoặc khi mất focus —
        // re-apply mỗi lần lấy lại focus để giữ trạng thái sticky immersive.
        if (hasFocus && immersiveEnabled()) applyImmersiveMode()
    }

    private fun applyImmersiveMode() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // Vuốt từ mép để bars hiện tạm thời rồi tự ẩn lại (sticky immersive).
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        super.onResume()
        screenStartAt = System.currentTimeMillis()
        AnalyticsEventsUtils.logScreenStart(screenName(), popupName())
    }

    override fun onPause() {
        super.onPause()
        val duration = ((System.currentTimeMillis() - screenStartAt) / 1000).toInt()
        AnalyticsEventsUtils.logScreenStop(
            screenName = screenName(),
            durationSec = duration,
            popupName = popupName()
        )
    }
}
