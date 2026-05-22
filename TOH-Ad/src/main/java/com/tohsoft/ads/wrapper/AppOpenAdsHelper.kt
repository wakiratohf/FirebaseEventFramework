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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.tohsoft.ad.R
import com.tohsoft.ads.AdsConfig
import com.tohsoft.ads.AdsConstants
import com.tohsoft.ads.models.LoadingState
import com.tohsoft.ads.utils.AdDebugLog
import com.tohsoft.ads.views.ProgressDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class AppOpenAdsHelper(context: Context, ids: List<String>, opaListener: AdOPAListener? = null) : AbsAdListeners() {
    private val TAG = "[${this::class.java.simpleName}] ${hashCode()} -- "
    private val mHandler = Handler(Looper.getMainLooper())

    private var mApplicationContext: Context? = context.applicationContext
    private var mWeakActivity: WeakReference<AppCompatActivity?>? = null

    private var mAppOpenAd: AppOpenAd? = null
    private var mLoadingDialog: Dialog? = null
    private var mLoadingFragment: ProgressDialogFragment? = null
    var mOPAListener: AdOPAListener? = opaListener

    private var isShowAsOPA = false
    private var mLoadingState: LoadingState? = LoadingState.NONE
    var isShowingAd = false
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
        isShowingAd = false
        isShowAsOPA = false
    }

    /**
     * Kiểm tra xem AppOpenAd có còn khả dụng để show hay không. AppOpenAd có thể cached được 4h kể từ khi load thành công
     * */
    fun checkAvailableAndPreLoad(): Long {
        return if (AdsConfig.getInstance().canShowAd() && !isLoaded()) {
            AdDebugLog.logd(TAG + "preLoad")
            mAppOpenAd = null
            loadedTimestamp = 0
            startLoadAppOpenAd()
            0
        } else {
            SystemClock.elapsedRealtime() - loadedTimestamp
        }
    }

    fun preLoad() {
        if (AdsConfig.getInstance().canShowAd() && !isLoaded()) {
            AdDebugLog.logd(TAG + "preLoad")
            startLoadAppOpenAd()
        }
    }

    private fun startLoadAppOpenAd() {
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
            AdDebugLog.logi(TAG + "Load AppOpenAd id " + adId)
            mApplicationContext?.let {
                mLoadingState = LoadingState.LOADING
                val adRequest = AdRequest.Builder().build()
                AppOpenAd.load(it, adId, adRequest, appOpenAdLoadCallback)
            }
        }
    }

    private val appOpenAdLoadCallback = object : AppOpenAd.AppOpenAdLoadCallback() {
        override fun onAdLoaded(appOpenAd: AppOpenAd) {
            super.onAdLoaded(appOpenAd)
            mLoadingState = LoadingState.FINISHED
            loadedTimestamp = SystemClock.elapsedRealtime()
            AdDebugLog.logi("$TAG onAdLoaded")
            // Save flag loaded
            AdsConfig.getInstance().onAdLoaded(mCurrentAdsId)

            // Set instance
            mAppOpenAd = appOpenAd
            mAppOpenAd?.fullScreenContentCallback = fullScreenContentCallback

            // Notify event
            mOPAListener?.onAdOPALoaded()
            notifyAdLoaded()
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
            loadedTimestamp = 0
            // Save flag load failed
            AdsConfig.getInstance().onAdFailedToLoad(mCurrentAdsId)
            // Reset instance
            mAppOpenAd = null
            // Notify event
            notifyAdLoadFailed(errorCode, message)
            return
        }
        startLoadAppOpenAd()
    }

    private val fullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
            super.onAdFailedToShowFullScreenContent(adError)
            AdDebugLog.loge("$TAG onAdFailedToShow: ${adError.message}")
            // TH gọi show nhưng bị lỗi không thể hiển thị
            onAdClosed()
        }

        override fun onAdShowedFullScreenContent() {
            super.onAdShowedFullScreenContent()
            if (isShowAsOPA) {
                // Show OPA -> lưu lại timestamp để check freq time
                AdsConfig.getInstance().saveInterOPAShowedTimestamp()
            }
            // Reset
            mAppOpenAd = null
            loadedTimestamp = 0
            // Notify event
            mOPAListener?.onAdOPAOpened()
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
        if (isShowAsOPA) {
            isShowAsOPA = false
            mOPAListener?.onAdOPACompleted()
            mOPAListener = null
        }
        notifyAdClosed()

        // Load lại Ads cho phiên sau
        preLoad()
    }

    private fun isLoadingAds(): Boolean {
        return mLoadingState == LoadingState.LOADING
    }

    private fun getAdId(): String? {
        if (mAdsIds.isEmpty()) return null

        if (mAdsPosition < mAdsIds.size) {
            if (AdsConfig.getInstance().isTestMode) {
                return AdsConstants.app_open_ads_test_id
            }
            mCurrentAdsId = mAdsIds[mAdsPosition].replace(AdsConstants.ADMOB_ID_PREFIX, "")
            return mCurrentAdsId
        } else {
            mAdsPosition = 0
        }
        return null
    }

    fun showAsOPA(activity: AppCompatActivity?): Boolean {
        try {
            AdDebugLog.logi(TAG + "showAsOPA")
            activity?.let {
                if (isLoaded() && AdsConfig.getInstance().canShowOPA()) {
                    it.lifecycleScope.launch(Dispatchers.Main) {
                        delay(300)
                        isShowingAd = true
                        isShowAsOPA = true
                        showLoading(it)
                        mAppOpenAd?.show(activity)
                        AdDebugLog.logi(TAG + "show AppOpenAd as OPA")
                    }
                    return true
                }
            }
        } catch (e: Exception) {
            AdDebugLog.loge(e)
            isShowingAd = false
            isShowAsOPA = false
            mOPAListener = null
            hideLoading()
        }
        return false
    }

    fun show(activity: AppCompatActivity?): Boolean {
        try {
            activity?.let {
                if (isLoaded() && it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    isShowingAd = true
                    showLoading(it)
                    mAppOpenAd?.show(activity)
                    AdDebugLog.logi(TAG + "show AppOpenAd")
                    return true
                }
            }
        } catch (e: Exception) {
            isShowingAd = false
            hideLoading()
        }
        return false
    }

    private fun registerLifecycleObserver(activity: AppCompatActivity?) {
        activity?.lifecycle?.addObserver(lifecycleObserver)
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {

        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)
            mWeakActivity?.clear()
            mWeakActivity = null
            isShowAsOPA = false
            owner.lifecycle.removeObserver(this)
        }
    }

    /**
     * Check if ad exists and can be shown.
     */
    fun isLoaded(): Boolean {
        return mAppOpenAd != null && isAvailable()
    }

    /**
     * Utility method to check if ad was loaded more than 4 hours ago.
     */
    private fun isAvailable(numHours: Float = 4f): Boolean {
        val dateDifference = SystemClock.elapsedRealtime() - loadedTimestamp
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
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
        resetStates()
        hideLoading()
        mAppOpenAd = null
        mHandler.removeCallbacksAndMessages(null)
    }

}