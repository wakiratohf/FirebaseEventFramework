package com.tohsoft.ads.analytics

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tohsoft.ads.AdsConfig
import com.tohsoft.ads.AdsModule

/**
 * Banner bottom (adaptive) của TOH-Ad bọc trong Composable, kèm bridge analytics.
 *
 * Tạo một [FrameLayout] container và nhờ [AdsModule.showBannerBottom] load/hiển thị
 * banner vào đó; sau đó gắn listener forward callback → `AdsEventTracker` qua
 * [AdsAnalytics.attachBanner]. Pause/resume theo lifecycle.
 *
 * Không render gì nếu [AdsConfig.canShowAd] == false.
 *
 * @param screenName màn đang hiển thị banner — gắn vào `click_ad_ev` để biết
 *   user click ad ở đâu (ví dụ `home`, `settings`).
 */
@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    screenName: String = AdType.BANNER,
) {
    if (!AdsConfig.getInstance().canShowAd()) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val container = remember { FrameLayout(context) }

    // Load banner + gắn analytics listener; gỡ listener khi rời composition.
    DisposableEffect(container, screenName) {
        val adsModule = AdsModule.getInstance()
        adsModule.showBannerBottom(context, container)
        // Wrapper được tạo bên trong showBannerBottom (null nếu điều kiện show không thỏa).
        val wrapper = adsModule.mBannerBottom
        val listener = wrapper?.let { AdsAnalytics.attachBanner(it, context, screenName) }
        onDispose {
            if (wrapper != null && listener != null) wrapper.removeListener(listener)
        }
    }

    // Pause/resume AdView theo lifecycle để tránh tính phí khi app ở nền.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val banner = AdsModule.getInstance().mBannerBottom
            when (event) {
                Lifecycle.Event.ON_RESUME -> banner?.resume()
                Lifecycle.Event.ON_PAUSE -> banner?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { container },
    )
}
