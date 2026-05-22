package com.tohsoft.ads.wrapper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import com.tohsoft.ads.AdsConfig
import com.tohsoft.ads.AdsConstants
import com.tohsoft.ads.utils.AdDebugLog

abstract class AdWrapper(context: Context, adIds: List<String>): AbsAdListeners() {
    open var TAG = "[${this::class.java.simpleName}] -- "

    abstract fun destroyAdInstance()
    abstract fun addAdsToContainer()

    private var mAdsIds: ArrayList<String> = ArrayList(adIds)
    var mAdsPosition: Int = 0
    var mCurrentAdsId: String = ""

    protected var mAdsContainer: ViewGroup? = null
    open var mContext: Context? = context
    open var isLoading: Boolean = false
    open var isLoaded: Boolean = false
    open val mHandler = Handler(Looper.getMainLooper())

    open var loadedTimestamp: Long = 0L

    fun setAdsId(adIds: List<String>) {
        mAdsIds.clear()
        mAdsIds.addAll(adIds)
    }

    open fun getCacheTime(): Long {
        return 0
    }

    open fun removeAdsFromContainer(){}
    open fun showLoadingView(container: ViewGroup?){}
    open fun hideLoadingView(removeImmediate: Boolean = false) {}
    open fun visibleAds(){}
    open fun invisibleAds(){}

    open fun getAdId(): String {
        if (mAdsIds.isEmpty()) return ""
        if (mAdsPosition >= mAdsIds.size) {
            mAdsPosition = 0
        }
        mCurrentAdsId = mAdsIds[mAdsPosition].replace(AdsConstants.ADMOB_ID_PREFIX, "")
        return mCurrentAdsId
    }

    fun canRetryLoadAdWhenLoadFailed(): Boolean {
        return mAdsIds.size > 1 && mAdsPosition < mAdsIds.size - 1
    }

    fun getNextAdsId() : String {
        mAdsPosition++
        return getAdId()
    }

    fun resetAdPosition() {
        mAdsPosition = 0
    }

    fun updateContainer(container: ViewGroup?) {
        container?.let { mAdsContainer = it }
    }

    fun deleteContainer() {
        mAdsContainer?.removeAllViews()
        mAdsContainer = null
    }

    fun getContainer(): ViewGroup? {
        return mAdsContainer
    }

    fun checkConditions(): Boolean {
        if (!AdsConfig.getInstance().canShowAd()) return false

        if (mContext == null) return false

        if (isLoading) {
//            AdDebugLog.logd("$TAG RETURN when Ad loading")
            showLoadingView(getContainer())
            return false
        }

        if (AdsConfig.getInstance().cantLoadId(mCurrentAdsId)) {
            AdDebugLog.loge("$TAG RETURN because this id just failed to load\nid: $mCurrentAdsId")
            notifyAdLoadFailed(-101, "Ads just failed to load.")
            return false
        }

        if (isAdAvailable()) {
//            AdDebugLog.logd("$TAG Ad loaded -> show Ad immediate")
            // Show Ads immediate
            addAdsToContainer()
            // Notify AdLoaded
            notifyAdLoaded()
            return false
        }
        return true
    }

    open fun isAdAvailable(): Boolean {
        return if (getCacheTime() > 0) {
            val dateDifference = SystemClock.elapsedRealtime() - loadedTimestamp
            val hasCache = dateDifference < getCacheTime()
//            if (hasCache) AdDebugLog.logd("hasCachedAd - passed time from loaded: ${dateDifference/1000} s")
            isLoaded && hasCache
        } else {
            isLoaded
        }
    }

    open fun adLoaded() {
        loadedTimestamp = SystemClock.elapsedRealtime()
        notifyAdLoaded()
    }

    open fun adLoadFailed(errorCode: Int, message: String?) {
        loadedTimestamp = 0
        notifyAdLoadFailed(errorCode, message)
    }

    override fun notifyAdLoaded() {
        // Mark flag isLoading & isLoaded
        isLoaded = true
        isLoading = false

        super.notifyAdLoaded()
    }

    override fun notifyAdLoadFailed(errorCode: Int, message: String?) {
        // Mark flag isLoading & isLoaded
        isLoaded = false
        isLoading = false

        super.notifyAdLoadFailed(errorCode, message)
    }

    /**
     * Destroy Ad
     * */
    open fun destroy() {
        AdDebugLog.logi("$TAG Destroy Ad")
        // Set flags
        isLoading = false
        isLoaded = false
        loadedTimestamp = 0
        // Remove callback
        mHandler.removeCallbacksAndMessages(null)
        // Remove Ads from container
        removeAdsFromContainer()
        // Delete Ad
        destroyAdInstance()
        // Delete container
        deleteContainer()
        // Clear listeners
        mAdListeners.clear()
    }
}