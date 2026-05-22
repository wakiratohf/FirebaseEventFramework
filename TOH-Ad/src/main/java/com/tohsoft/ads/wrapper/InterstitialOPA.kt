package com.tohsoft.ads.wrapper

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.tohsoft.ad.R
import com.tohsoft.ads.AdsConfig
import com.tohsoft.ads.AdsConstants
import com.tohsoft.ads.models.CountingState
import com.tohsoft.ads.models.LoadingState
import com.tohsoft.ads.utils.AdDebugLog
import com.tohsoft.ads.views.ProgressDialogFragment

class InterstitialOPA(context: Context, ids: List<String>, opaListener: AdOPAListener? = null) : AbsAdListeners() {
    private val TAG = "[${this::class.java.simpleName}] ${hashCode()} -- "
    private val mHandler = Handler(Looper.getMainLooper())
    private val mMinDelayTime = 1000L // Sếp Nhân yêu cầu show tối thiểu 1s Splash rồi mới show InterOPA

    private var mApplicationContext: Context? = context.applicationContext
    private var mWeakActivity: AppCompatActivity? = null

    private var mInterstitialAd: InterstitialAd? = null
    private var mCounter: CountDownTimer? = null
    private var mLoadingDialog: Dialog? = null
    private var mLoadingFragment: ProgressDialogFragment? = null
    var mOPAListener: AdOPAListener? = opaListener

    var isShowingAd = false
    private var isShownOnStartUp = false
    private var mCountingState: CountingState? = CountingState.NONE
    private var mLoadingState: LoadingState? = LoadingState.NONE
    var mProgressBgResourceId: Int = 0
    var mCustomProgressView: View? = null

    private var mAdsIds: List<String> = ids
    private var mAdsPosition = 0
    private var mCurrentAdsId: String? = null

    /**
     * Keep track of the time an Inter ad is loaded to ensure you don't show an expired ad.
     */
    private var loadedTimestamp: Long = 0

    fun setAdsId(ids: List<String>) {
        mAdsIds = ids
    }

    fun setOpaListener(opaListener: AdOPAListener?) {
        opaListener?.let {
            mOPAListener = opaListener
        }
    }

    fun resetStates() {
        mCountingState = CountingState.NONE
        mLoadingState = LoadingState.NONE
        isShowingAd = false
        isShownOnStartUp = false
    }

    fun isCounting(): Boolean {
        return mCountingState == CountingState.COUNTING
    }

    private fun isLoadingAds(): Boolean {
        return mLoadingState == LoadingState.LOADING
    }

    fun initAndShowOPA(activity: AppCompatActivity) {
        mApplicationContext = activity.applicationContext
        mWeakActivity = activity
        registerLifecycleObserver(activity)

        // Start load Ad & counter
        startLoadInterstitial()
        startOPALoadingCounter(activity)
    }

    fun preLoad() {
        if (AdsConfig.getInstance().canShowAd() && !isLoaded()) {
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

    private fun startLoadInterstitial() {
        if (isLoaded()) {
            AdDebugLog.logi(TAG + "RETURN when Ads isLoaded")
            return
        }
        if (isLoadingAds()) {
            AdDebugLog.logi(TAG + "RETURN when Ads isLoading")
            return
        }
        val adId = getAdId()
        if (AdsConfig.getInstance().cantLoadId(adId)) {
            AdDebugLog.loge("$TAG RETURN because this id just failed to load\nid: $adId")
            return
        }

        adId?.let {
            // Init Ads
            AdDebugLog.logi(TAG + "Load Inter OPA id " + adId)
            mApplicationContext?.let {
                mLoadingState = LoadingState.LOADING
                val adRequest = AdRequest.Builder().build()
                InterstitialAd.load(mWeakActivity ?: it, adId, adRequest, interstitialAdLoadCallback)
            }
        }
    }

    private val interstitialAdLoadCallback = object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(interstitialAd: InterstitialAd) {
            super.onAdLoaded(interstitialAd)
            mLoadingState = LoadingState.FINISHED
            loadedTimestamp = SystemClock.elapsedRealtime()
            AdDebugLog.logi("$TAG onAdLoaded:\nisCounting: ${isCounting()}, activityState: ${mWeakActivity?.lifecycle?.currentState}")
            // Save flag loaded
            AdsConfig.getInstance().onAdLoaded(mCurrentAdsId)

            // Set instance
            mInterstitialAd = interstitialAd
            mInterstitialAd?.fullScreenContentCallback = fullScreenContentCallback

            // Notify event
            mOPAListener?.onAdOPALoaded()
            notifyAdLoaded()

            // Check flow counter OPA
            if (isCounting() && mWeakActivity?.lifecycle?.currentState?.isAtLeast(State.STARTED) == true) {
                onOPAFinished(mWeakActivity)
            }
        }

        override fun onAdFailedToLoad(error: LoadAdError) {
            super.onAdFailedToLoad(error)
            loadedTimestamp = 0
            val message = if (error.message.isNotEmpty()) "\nErrorMsg: ${error.message}" else ""
            val errorMsg = "\nErrorCode: ${error.code}" + message + "\nid: $mCurrentAdsId"
            AdDebugLog.loge("$TAG onAdFailedToLoad: $errorMsg")
            // Save flag load failed
            AdsConfig.getInstance().onAdFailedToLoad(mCurrentAdsId)
            // Retry load Ad
            retryLoadAd(error.code, message)
        }
    }

    private fun retryLoadAd(errorCode: Int, message: String?) {
        mAdsPosition++
        val adId = getAdId()
        if (adId == null) {
            mLoadingState = LoadingState.FINISHED
            // Reset instance
            mInterstitialAd = null
            // Notify event
            notifyAdLoadFailed(errorCode, message)
            // Check flow counter OPA
            if (isCounting()) {
                stopCounter()
                onOPAFinished(mWeakActivity)
            }
            return
        }
        startLoadInterstitial()
    }

    private val fullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
            super.onAdFailedToShowFullScreenContent(adError)
            AdDebugLog.loge("$TAG \nonAdFailedToShow: ${adError.message}")
            // TH gọi show nhưng bị lỗi không thể hiển thị
            onAdClosed()
        }

        override fun onAdShowedFullScreenContent() {
            super.onAdShowedFullScreenContent()
            if (isShownOnStartUp) {
                // Show OPA -> lưu lại timestamp để check freq time
                AdsConfig.getInstance().saveInterOPAShowedTimestamp()
                // Notify event
                mOPAListener?.onAdOPAOpened()
            }
            // Reset
            mInterstitialAd = null
            loadedTimestamp = 0
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
        mInterstitialAd = null
        // Notify event
        if (isShownOnStartUp) {
            isShownOnStartUp = false
            mOPAListener?.onAdOPACompleted()
        }
        notifyAdClosed()

        // Load lại Ads cho phiên sau
        preLoad()
    }

    private fun startOPALoadingCounter(activity: AppCompatActivity) {
        if (mCountingState != CountingState.NONE) {
            AdDebugLog.loge(TAG + "RETURN counter when mCountingState != NONE: " + mCountingState)
            return
        }

        isShownOnStartUp = false
        if (!AdsConfig.getInstance().canShowOPA()) {
            AdDebugLog.loge(TAG + "RETURN counter when can't showOPA")
            onOPAFinished(activity)
            return
        }

        mCountingState = CountingState.COUNTING
        val counterTimeout = AdsConfig.getInstance().interOPAProgressDelayInMs + AdsConfig.getInstance().interOPASplashDelayInMs
        val minimumDelay = if (counterTimeout < mMinDelayTime) counterTimeout else mMinDelayTime
        val interval = 100L
        AdDebugLog.logd("$TAG\ncounterTimeout: $counterTimeout")
        mCounter = object : CountDownTimer(counterTimeout, interval) {
            override fun onTick(millisUntilFinished: Long) {
                // Check if ad OPA loaded
                val passedTime = counterTimeout - millisUntilFinished
                if (passedTime >= minimumDelay && mInterstitialAd != null && activity.lifecycle.currentState.isAtLeast(State.STARTED)) {
                    AdDebugLog.logi("$TAG\nInterstitial loaded when counting -> stop counter and show immediate\npassedTime: $passedTime")
                    stopCounter()
                    onOPAFinished(activity)
                }
            }

            override fun onFinish() {
                AdDebugLog.logd("$TAG\nCounter FINISHED")
                onOPAFinished(activity)
            }
        }
        mCounter?.start()
    }

    private fun onOPAFinished(activity: AppCompatActivity?) {
        if (mCountingState == CountingState.COUNT_FINISHED) return

        AdDebugLog.loge("onOPAFinished")
        mCountingState = CountingState.COUNT_FINISHED
        isShownOnStartUp = show(activity)
        if (!isShowingAd) { // Ads not showing
            mOPAListener?.onAdOPACompleted()
            hideLoading()
        }
    }

    private var session: Int = 0

    private fun registerLifecycleObserver(activity: AppCompatActivity?) {
        activity?.lifecycle?.apply {
            addObserver(lifecycleObserver)
            session = hashCode()
        }
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {

        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)
            owner.lifecycle.removeObserver(this)
            if (owner.lifecycle.hashCode() == session) {
                mWeakActivity = null
                stopCounter()
            }
        }
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

    fun showImmediate(activity: AppCompatActivity?): Boolean {
        try {
            activity?.let {
                if (isLoaded()) {
                    isShowingAd = true
                    isShownOnStartUp = false
                    showLoading(it)
                    mInterstitialAd?.show(activity)
                    AdDebugLog.logi(TAG + "showImmediate")
                    return true
                }
            }
        } catch (e: Exception) {
            isShowingAd = false
            hideLoading()
        }
        return false
    }

    fun show(activity: AppCompatActivity?): Boolean {
        try {
            activity?.let {
                if (isLoaded() && AdsConfig.getInstance().canShowOPA() && it.lifecycle.currentState.isAtLeast(State.STARTED)) {
                    isShowingAd = true
                    showLoading(it)
                    mInterstitialAd?.show(activity)
                    AdDebugLog.logi(TAG + "show InterstitialOpenApp")
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

    private fun stopCounter() {
        AdDebugLog.loge("stopCounter")
        mCounter?.cancel()
        mCounter = null
    }

    fun destroy() {
        stopCounter()
        resetStates()
        hideLoading()
        mInterstitialAd = null
        mHandler.removeCallbacksAndMessages(null)
    }
}