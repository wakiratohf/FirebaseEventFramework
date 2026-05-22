package com.tohsoft.ads.wrapper

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.tohsoft.ad.R
import com.tohsoft.ads.AdsConfig
import com.tohsoft.ads.AdsConstants
import com.tohsoft.ads.models.BannerType
import com.tohsoft.ads.utils.AdDebugLog
import com.tohsoft.ads.utils.AdmobLoader
import com.tohsoft.ads.utils.AdsUtils

/**
 * Created by Phong on 11/16/2018.
 */
class AdViewWrapper(context: Context, adsId: List<String>, bannerType: BannerType) : AdWrapper(context, adsId) {

    init {
        TAG = "[${this::class.java.simpleName}] ${hashCode()} -- "
    }

    private var mAdView: AdView? = null
    private var mLoadingView: View? = null
    private var isUseAdaptiveBanner: Boolean = true
    private val mBannerType = bannerType

    private fun canVisibleAdView(): Boolean {
        return AdsConfig.getInstance().hasWindowFocus()
                || (mBannerType == BannerType.EXIT_DIALOG && !AdsConfig.getInstance().isHideAllAds())
    }

    override fun visibleAds() {
        if (canVisibleAdView()) {
            mAdView?.visibility = View.VISIBLE
        }
    }

    override fun invisibleAds() {
        mAdView?.visibility = View.INVISIBLE
    }

    fun getAdView(): AdView? {
        return mAdView
    }

    fun showBottomBanner(container: ViewGroup? = null) {
        updateContainer(container)
        getAdId()
        if (!checkConditions()) return
        if (!AdsUtils.isInternetAvailable()) return

        initNormalAdView()
    }

    @JvmOverloads
    fun showMediumBanner(container: ViewGroup? = null) {
        updateContainer(container)
        getAdId()
        if (!checkConditions()) return
        if (!AdsUtils.isInternetAvailable()) return

        initMediumAdView()
    }

    private fun initMediumBanner() {
        showMediumBanner(null)
    }

    override fun getAdId(): String {
        return if (AdsConfig.getInstance().isTestMode) {
            mCurrentAdsId = AdsConstants.banner_test_id
            AdsConstants.banner_test_id
        } else {
            super.getAdId()
        }
    }

    /**
     * Init & show adaptive banner for empty screen | exit dialog
     * size: fit screen width, height ~60dp
     * */
    private fun initNormalAdView() {
        isLoading = true
        isLoaded = false

        val adId = getAdId()
        // Notify bắt đầu load → analytics load_ad_ev (action load/retry).
        notifyAdStartLoad()
        val adListener = object : AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                val message = if (error.message.isNotEmpty()) "\nErrorMsg: ${error.message}" else ""
                val errorMsg = "\nErrorCode: ${error.code}" + message + "\nid: $adId"
                AdDebugLog.loge("$TAG AdaptiveBanner $errorMsg")
                // Mark flag for this id just load failed
                AdsConfig.getInstance().onAdFailedToLoad(mCurrentAdsId)
                // try to reload Ad
                onAdLoadFailed(true, error.code, message)
            }

            override fun onAdLoaded() {
                super.onAdLoaded()
                AdDebugLog.logi("$TAG \nAdaptiveBanner loaded - mAdId: $adId")
                // Mark flag for this id just loaded
                AdsConfig.getInstance().onAdLoaded(adId)
                // Notify Ad loaded
                adLoaded()
                // Add Ad to container
                addAdsToContainer()
            }

            override fun onAdImpression() {
                super.onAdImpression()
                // Notify impression → analytics show_ad_ev.
                notifyAdImpression()
            }

            override fun onAdClicked() {
                super.onAdClicked()
                // Notify event
                notifyAdClicked()
                // Reload Ad
                reloadWhenAdClicked(true)
            }
        }

        mContext?.let {
            if (isUseAdaptiveBanner) {
                mAdView = AdmobLoader.initAdaptiveBanner(it.applicationContext, adId, adListener)
                AdDebugLog.logi("$TAG Start load AdaptiveBanner id $adId")
            } else {
                mAdView = AdmobLoader.initNormalBanner(it.applicationContext, adId, adListener)
                AdDebugLog.logi("$TAG Start load NormalBanner id $adId")
            }
            // Doanh thu impression-level → analytics paid_ad_impression.
            mAdView?.setOnPaidEventListener { adValue ->
                notifyPaidEvent(adValue, adId, mAdView?.responseInfo?.mediationAdapterClassName)
            }
        }

        // Show loading
        showLoadingView(container = getContainer())
    }

    /**
     * Init & show medium banner for empty screen | exit dialog
     * size: 300 x 250
     * */
    private fun initMediumAdView() {
        isLoading = true
        isLoaded = false

        val adId = getAdId()
        val adListener = object : AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                val message = if (error.message.isNotEmpty()) "\nErrorMsg: ${error.message}" else ""
                val errorMsg = "\nErrorCode: ${error.code}" + message + "\nid: $adId"
                AdDebugLog.loge("$TAG MediumAdView $errorMsg")
                // Mark flag for this id just load failed
                AdsConfig.getInstance().onAdFailedToLoad(mCurrentAdsId)
                // try to reload Ad
                onAdLoadFailed(false, error.code, message)
            }

            override fun onAdLoaded() {
                super.onAdLoaded()
                AdDebugLog.logd("$TAG MediumAdView loaded - mAdId: $mCurrentAdsId \nBannerType: $mBannerType")
                // Mark flag for this id just loaded
                AdsConfig.getInstance().onAdLoaded(mCurrentAdsId)
                // Notify Ad loaded
                adLoaded()
                // Add Ad to container
                addAdsToContainer()
            }

            override fun onAdClicked() {
                super.onAdClicked()
                // Notify event
                notifyAdClicked()
                // Reload Ad
                reloadWhenAdClicked(false)
            }
        }

        mContext?.let {
            mAdView = if (mBannerType == BannerType.LARGE) {
                AdDebugLog.logi("$TAG Start load LargeAdView id $adId")
                AdmobLoader.initLargeBanner(it.applicationContext, adId, adListener)
            } else {
                AdDebugLog.logi("$TAG Start load MediumAdView id $adId")
                AdmobLoader.initMediumBanner(it.applicationContext, adId, adListener)
            }
        }
    }

    /**
     *
     * */
    private fun onAdLoadFailed(isBottomBanner: Boolean, errorCode: Int, errorMsg: String?) {
        if (canRetryLoadAdWhenLoadFailed()) {
            // remove ad from container and delete ad instance
            removeAdsFromContainer()
            // Get next ad id in list
            getNextAdsId()
            // Post delay to avoid ANR
            val delayTime: Long = (mAdsPosition + 1) * 2_000L
            delayToLoadAd(isBottomBanner, delayTime)
        } else {
            // Reset ad position
            resetAdPosition()
            // Remove Ads
            removeAdsFromContainer()
            // Hide loading view
            hideLoadingView(removeImmediate = true)
            // Destroy instance
            destroyAdInstance()
            // Notify load failed
            adLoadFailed(errorCode, errorMsg)
        }
    }

    private fun delayToLoadAd(isBottomBanner: Boolean, delayTime: Long) {
        mHandler.postDelayed({
            destroyAdInstance()
            if (isBottomBanner) {
                showBottomBanner()
            } else {
                initMediumBanner()
            }
        }, delayTime)
    }

    override fun showLoadingView(container: ViewGroup?) {
        if (mBannerType != BannerType.BOTTOM) { // Chỉ show place holder với bottom banner
            hideLoadingView(true)
            return
        }

        if (mLoadingView == null) {
            mLoadingView = LayoutInflater.from(mContext).inflate(R.layout.banner_ad_bottom_loading, null)
        }

        val loadingParentView = mLoadingView!!.parent
        if (loadingParentView != null && mAdsContainer != null && loadingParentView == mAdsContainer) {
            return
        }
        hideLoadingView()

        mAdsContainer?.let { adContainer ->
            mLoadingView?.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            adContainer.removeAllViews()
            val containerLayoutParams = adContainer.layoutParams
            containerLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            adContainer.layoutParams = containerLayoutParams
            adContainer.addView(mLoadingView)
        }
    }

    override fun hideLoadingView(removeImmediate: Boolean) {
        mLoadingView?.let { loadingView ->
            (loadingView.parent as? ViewGroup)?.let { container ->
                try {
                    if (removeImmediate) {
                        container.removeView(loadingView)
                    } else if (getContainer() != null && container.hashCode() != getContainer()?.hashCode()) {
//                        AdDebugLog.logd("$TAG \nremoveLoadingViewFromContainer: ${getContainer()?.hashCode()}")
                        container.removeView(loadingView)
                    }
                } catch (e: Exception) {
                    AdDebugLog.loge(e)
                }
            }
        }
    }

    /**
     * Reload when Ad clicked
     * */
    private fun reloadWhenAdClicked(isBottomBanner: Boolean) {
        removeAdsFromContainer()
        destroyAdInstance()
        if (isBottomBanner) {
            showBottomBanner()
        } else {
            initMediumBanner()
        }
    }

    override fun addAdsToContainer() {
        hideLoadingView(removeImmediate = true)
        if (canVisibleAdView()) {
            visibleAds()
        } else {
            invisibleAds()
        }
        AdsUtils.addAdsToContainer(getContainer(), mAdView)
    }

    override fun removeAdsFromContainer() {
        (mAdView?.parent as? ViewGroup)?.removeAllViews()
    }

    fun pause() {
        mAdView?.pause()
    }

    fun resume() {
        mAdView?.resume()
    }

    fun detachAdWhenAppKilled() {
        removeAdsFromContainer()
        hideLoadingView()
        deleteContainer()
    }

    fun isAdInThisContainer(container: ViewGroup?): Boolean {
        return container != null && mAdView != null && mAdView!!.parent != null && container.hashCode() == mAdView!!.parent.hashCode()
    }

    override fun destroyAdInstance() {
        // Set flags
        isLoading = false
        isLoaded = false
        // Destroy Ad instance
        mAdView?.destroy()
        mAdView = null
    }

}
