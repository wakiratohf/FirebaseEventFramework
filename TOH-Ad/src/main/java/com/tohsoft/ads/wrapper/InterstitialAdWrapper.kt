package com.tohsoft.ads.wrapper

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle.State
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.tohsoft.ad.R
import com.tohsoft.ads.AdsConfig
import com.tohsoft.ads.AdsConstants
import com.tohsoft.ads.models.LoadingState
import com.tohsoft.ads.utils.AdDebugLog
import com.tohsoft.ads.views.ProgressDialogFragment

class InterstitialAdWrapper(context: Context, ids: List<String>) : AbsAdListeners() {
    private val TAG = "[${this::class.java.simpleName}] ${hashCode()} -- "
    private val mHandler = Handler(Looper.getMainLooper())
    private var mApplicationContext: Context? = context.applicationContext

    private var mInterstitialAd: InterstitialAd? = null
    private var mLoadingDialog: Dialog? = null
    private var mLoadingFragment: ProgressDialogFragment? = null
    private var mLoadingState: LoadingState? = LoadingState.NONE

    private var isShowingAd = false
    private var mProgressBgResourceId: Int = 0
    private var mCustomProgressView: View? = null

    private var mAdsIds: List<String> = ids
    private var mAdsPosition = 0
    private var mCurrentAdsId: String? = null

    /**
     * Keep track of the time an Inter ad is loaded to ensure you don't show an expired ad.
     */
    private var loadedTimestamp: Long = 0

    fun preLoad() {
        if (AdsConfig.getInstance().canShowAd() && !isLoaded() && !isShowingAd) {
            AdDebugLog.logd(TAG + "preLoad")
            startLoadInterstitial()
        }
    }

    private fun getAdId(): String? {
        if (mAdsIds.isEmpty()) return null

        if (mAdsPosition < mAdsIds.size) {
            if (AdsConfig.getInstance().isTestMode) {
                return AdsConstants.interstitial_test_id
            }
            mCurrentAdsId = mAdsIds[mAdsPosition].replace(AdsConstants.ADMOB_ID_PREFIX, "")
            return mCurrentAdsId
        } else {
            mAdsPosition = 0
        }
        return null
    }

    private fun isLoadingAds(): Boolean {
        return mLoadingState == LoadingState.LOADING
    }

    private fun startLoadInterstitial() {
        if (isLoaded()) {
            AdDebugLog.logi(TAG + "RETURN when Ads isLoaded")
            return
        }
        if (isShowingAd) {
            AdDebugLog.logi(TAG + "RETURN when Ads isShowing")
            return
        }
        if (isLoadingAds()) {
            AdDebugLog.logi(TAG + "RETURN when Ads isLoading")
            return
        }
        val adId = getAdId()
        if (AdsConfig.getInstance().cantLoadId(adId) || adId == null) {
            AdDebugLog.loge("$TAG RETURN because this id just failed to load\nid: $adId")
            return
        }

        // Init Ads
        AdDebugLog.logi(TAG + "Load Inter OPA id " + adId)
        mApplicationContext?.let {
            mLoadingState = LoadingState.LOADING
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(it, adId, adRequest, interstitialAdLoadCallback)
        }
    }

    private val interstitialAdLoadCallback = object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(interstitialAd: InterstitialAd) {
            super.onAdLoaded(interstitialAd)
            mLoadingState = LoadingState.FINISHED
            loadedTimestamp = SystemClock.elapsedRealtime()
            AdDebugLog.logi("$TAG onAdLoaded")
            // Save flag loaded
            AdsConfig.getInstance().onAdLoaded(mCurrentAdsId)

            // Set instance
            mInterstitialAd = interstitialAd
            mInterstitialAd?.fullScreenContentCallback = fullScreenContentCallback

            // Notify event
            notifyAdLoaded()
        }

        override fun onAdFailedToLoad(error: LoadAdError) {
            super.onAdFailedToLoad(error)
            mLoadingState = LoadingState.FINISHED
            loadedTimestamp = 0
            val message = if (error.message.isNotEmpty()) "\nErrorMsg: ${error.message}" else ""
            val errorMsg = "\nErrorCode: ${error.code}" + message + "\nid: $mCurrentAdsId"
            AdDebugLog.loge("$TAG onAdFailedToLoad: $errorMsg")
            // Save flag load failed
            AdsConfig. getInstance().onAdFailedToLoad(mCurrentAdsId)

            mInterstitialAd = null
            // Notify event
            notifyAdLoadFailed(error.code, message)
        }
    }

    private val fullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
            super.onAdFailedToShowFullScreenContent(adError)
            // TH gọi show nhưng bị lỗi không thể hiển thị
            onAdClosed()
        }

        override fun onAdShowedFullScreenContent() {
            super.onAdShowedFullScreenContent()
            // Show OPA -> lưu lại timestamp để check freq time
            AdsConfig.getInstance().saveInterstitialShowedTimestamp()
            // Reset
            loadedTimestamp = 0
            mInterstitialAd = null
            // Notify event
            notifyAdOpened()
        }

        override fun onAdDismissedFullScreenContent() {
            super.onAdDismissedFullScreenContent()
            onAdClosed()
        }
    }

    private fun onAdClosed() {
        hideLoading()
        // Reset flag
        isShowingAd = false
        loadedTimestamp = 0
        // Notify event
        notifyAdClosed()

        // Load lại Ads cho phiên sau
        preLoad()
    }

    /**
     * Check if ad exists and can be shown.
     */
    fun isLoaded(): Boolean {
        return mInterstitialAd != null && isAvailable()
    }

    /**
     * Utility method to check if ad was loaded more than 1 hours ago.
     */
    private fun isAvailable(): Boolean {
        val dateDifference = SystemClock.elapsedRealtime() - loadedTimestamp
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour
    }

    fun show(activity: AppCompatActivity?): Boolean {
        try {
            activity?.let {
                if (isLoaded() && AdsConfig.getInstance().canShowInterstitial() && it.lifecycle.currentState.isAtLeast(State.STARTED)) {
                    isShowingAd = true
                    showLoading(it)
                    mInterstitialAd?.show(activity)
                    AdDebugLog.logi(TAG + "show Interstitial")
                    return true
                }
            }
        } catch (e: Exception) {
            isShowingAd = false
            hideLoading()
        }
        return false
    }

    @SuppressLint("InflateParams")
    fun showLoading(activity: AppCompatActivity) {
        try {
            if (mLoadingDialog?.isShowing == true) return
            if (mLoadingFragment?.isAdded == true) return

            if (mProgressBgResourceId == 0 && mCustomProgressView == null) {
                mLoadingDialog = ProgressDialog(activity).apply {
                    setTitle(activity.getString(R.string.msg_dialog_please_wait))
                    setMessage(activity.getString(R.string.msg_dialog_loading_data))
                    setCancelable(false)
                    show()
                }
                return
            }

            if (mCustomProgressView == null) {
                mCustomProgressView = activity.layoutInflater.inflate(R.layout.progress_layout, null)
                mCustomProgressView?.findViewById<ImageView>(R.id.iv_background)?.apply {
                    setImageResource(if (mProgressBgResourceId != 0) mProgressBgResourceId else R.drawable.bg_black_alpha_corner)
                    alpha = 0.93f
                }
            } else if (mCustomProgressView?.parent is ViewGroup) {
                (mCustomProgressView?.parent as ViewGroup).removeView(mCustomProgressView)
            }

            mLoadingFragment = ProgressDialogFragment(mCustomProgressView)
            activity.supportFragmentManager.beginTransaction().apply {
                add(android.R.id.content, mLoadingFragment!!).commitNow()
            }
        } catch (e: Exception) {
            AdDebugLog.loge(e)
        }
    }

    private fun hideLoading() {
        if (mLoadingDialog?.isShowing == true) {
            mLoadingDialog?.dismiss()
        }
        mLoadingDialog = null

        if (mLoadingFragment?.isVisible == true) {
            mLoadingFragment?.dismiss()
        }
        mLoadingFragment = null
    }

    fun destroy() {
        hideLoading()
        mInterstitialAd = null
        mHandler.removeCallbacksAndMessages(null)
    }
}