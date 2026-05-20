package com.tohsoft.ads

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.tohsoft.ads.models.AdAction
import com.tohsoft.ads.models.AdResult
import com.tohsoft.ads.models.AdType
import com.tohsoft.app_event.AdsEventTracker

/**
 * Anchored adaptive banner, full-width — neo đáy màn hình giống `banner_bottom`
 * của app toh-weather. Tự load khi vào composition, pause/resume theo lifecycle
 * và destroy khi rời composition (tránh leak [AdView]).
 *
 * Không render gì nếu [AdsConfig.canShowAds] == false.
 *
 * Forward callback AdMob vào [AdsEventTracker]: `load_ad_ev` (load/loaded/
 * load_failed), `show_ad_ev` (impression), `click_ad_ev`, `paid_ad_impression`.
 *
 * @param adUnitId mặc định lấy từ [AdsConfig]; truyền giá trị khác để dùng id riêng.
 * @param screenName màn đang hiển thị banner — gắn vào `click_ad_ev` để biết
 *   user click ad ở đâu (ví dụ `home`, `settings`).
 */
@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String = AdsConfig.bannerAdUnitId,
    screenName: String = AdType.BANNER,
) {
    if (!AdsConfig.canShowAds()) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Bề rộng khả dụng (dp) để tính chiều cao adaptive banner theo thiết bị/orientation.
    val widthDp = LocalConfiguration.current.screenWidthDp

    val adView = remember(adUnitId, widthDp) {
        // Thời điểm impression gần nhất, để tính duration cho click_ad_ev (giây).
        var shownAt = 0L
        AdView(context).apply {
            setAdSize(adaptiveAdSize(context, widthDp))
            this.adUnitId = adUnitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    AdsEventTracker.logLoadAd(AdType.BANNER, AdAction.LOADED, adUnitId)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdsEventTracker.logLoadAd(AdType.BANNER, AdAction.LOAD_FAILED, adUnitId)
                }

                override fun onAdImpression() {
                    shownAt = System.currentTimeMillis()
                    AdsEventTracker.logShowAd(context, AdType.BANNER, AdResult.SUCCESS, adUnitId)
                }

                override fun onAdClicked() {
                    val duration =
                        if (shownAt == 0L) 0
                        else ((System.currentTimeMillis() - shownAt) / 1000L).toInt()
                    AdsEventTracker.logClickAd(AdType.BANNER, screenName, duration)
                }
            }
            // Doanh thu impression-level → paid_ad_impression.
            setOnPaidEventListener { adValue ->
                AdsEventTracker.logPaidAd(
                    AdMobAdRevenue(
                        adValue = adValue,
                        adUnitId = adUnitId,
                        adSource = responseInfo?.mediationAdapterClassName,
                    )
                )
            }
            AdsEventTracker.logLoadAd(AdType.BANNER, AdAction.LOAD, adUnitId)
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { adView },
    )
}

private fun adaptiveAdSize(context: Context, widthDp: Int): AdSize =
    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
