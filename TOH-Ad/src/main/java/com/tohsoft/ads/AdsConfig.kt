package com.tohsoft.ads

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.text.TextUtils
import com.tohsoft.ads.models.AdsType
import com.tohsoft.ads.utils.AdDebugLog
import com.tohsoft.ads.utils.SharedPreference
import org.json.JSONObject

@Suppress("FunctionName")
class AdsConfig {
    companion object {
        private const val APP_OPEN_AD_SHOWED_TIMESTAMP = "app_open_ad_showed_timestamp"
        private const val INTERSTITIAL_OPA_SHOWED_TIMESTAMP = "interstitial_opa_showed_timestamp"
        private const val INTERSTITIAL_SHOWED_TIMESTAMP = "interstitial_showed_timestamp"
        private const val FREQ_INTER_OPA_IN_MILLISECONDS = "freq_interstitial_opa_in_ms"
        private const val INTER_OPA_SPLASH_DELAY_IN_MS = "inter_opa_splash_delay_in_ms"
        private const val INTER_OPA_PROGRESS_DELAY_IN_MS = "inter_opa_progress_delay_in_ms"
        private const val FREQ_CAP_INTER_IN_MS = "freq_cap_inter_in_ms"
        private const val MINIMUM_TIME_SHOW_INTER_IN_MS = "minimum_time_show_inter_in_ms"
        private const val ADS_ENABLE_STATE = "ads_enable_state"
        private const val WAITING_TIME_WHEN_LOAD_FAILED = "waiting_time_when_load_failed"
        private const val FREQ_CAP_APP_OPEN_AD_IN_MS = "freq_cap_app_open_ad_in_ms"

        private const val DEFAULT_FREQ_CAP_APP_OPEN_AD_IN_MS = (5 * 60 * 1000).toLong()// 5 minutes
        private const val DEFAULT_FREQ_CAP_INTER_OPA_IN_MS = (15 * 60 * 1000).toLong()// 15 minutes
        private const val DEFAULT_INTER_OPA_SPLASH_DELAY_IN_MS: Long = 3000 // 3 seconds
        private const val DEFAULT_INTER_OPA_PROGRESS_DELAY_IN_MS: Long = 2000 // 2 seconds
        private const val DEFAULT_FREQ_CAP_INTER_IN_MS = (15 * 60 * 1000).toLong() // 15 minutes
        private const val DEFAULT_WAITING_TIME_WHEN_LOAD_FAILED_IN_MS = (5 * 1000).toLong()  // 5s

        private val sInstance: AdsConfig by lazy {
            AdsConfig()
        }

        @JvmStatic
        fun getInstance(): AdsConfig {
            return sInstance
        }
    }


    private var mApplication: Application? = null

    /**
     * Ad id load failed timestamp
     * */
    private val mAdLoadFailedTimestamp = HashMap<String, Long>()

    /**
     * Ad type enable state
     * key = {@link [com.tohsoft.ads.models.AdsType]
     * */
    private val mAdsEnableState = HashMap<String, Boolean>()

    private var hasWindowFocus = true
    private var isHideAllAds = false
    var waitingTimeWhenLoadFailedInMs = DEFAULT_WAITING_TIME_WHEN_LOAD_FAILED_IN_MS
    var isFullVersion = false
        private set
    var isTestMode = false
    var isTestGDPR = false
    var isCacheAds = false
    var freqAppOpenAdInMs: Long = 0
        set(value) {
            field = value
        }

    var accentColor: Int = Color.parseColor("#4A9DFF")
    var primaryTextColor: Int = -1
    var secondaryTextColor: Int = -1

    fun mustInit(): Boolean {
        return mApplication == null
    }

    // Init
    fun init(application: Application?): AdsConfig {
        mApplication = application
        freqAppOpenAdInMs = SharedPreference.getLong(application, FREQ_CAP_APP_OPEN_AD_IN_MS, DEFAULT_FREQ_CAP_APP_OPEN_AD_IN_MS)
        waitingTimeWhenLoadFailedInMs = SharedPreference.getLong(mApplication, WAITING_TIME_WHEN_LOAD_FAILED, DEFAULT_WAITING_TIME_WHEN_LOAD_FAILED_IN_MS)
        return this@AdsConfig
    }

    fun initAdsState(context: Context): AdsConfig {
        if (mAdsEnableState.isEmpty()) {
            val data = SharedPreference.getString(context, ADS_ENABLE_STATE, "{}")
            data?.let { setAdsEnableState(it) }
        }
        return this@AdsConfig
    }

    fun canShowAd(): Boolean {
        return !isFullVersion && mApplication != null && GoogleConsentManager.getInstance(mApplication!!).canRequestAds()
    }

    /**
     * Ẩn hết các loại Ads khác trong UI, trừ Ads ở dialog weather news vì dialog này sẽ overlay lên toàn bộ UI khác
     */
    fun hideAllAds(hideAllAds: Boolean): AdsConfig {
        isHideAllAds = hideAllAds
        AdsModule.getInstance().hideAllAds()
        return this@AdsConfig
    }

    /**
     * Khi Activity bị mất focus (inactive), VD như show dialog thì sẽ ẩn hết Ads hiển thị ở UI đi, trừ Ads show ở dialog
     */
    fun onWindowFocusChanged(hasFocused: Boolean): AdsConfig {
        hasWindowFocus = hasFocused
        AdsModule.getInstance().onWindowFocusChanged()
        return this@AdsConfig
    }

    /**
     * Load success/failed timestamp của một id cụ thể
     * Chức năng giới hạn thời gian để fix lỗi load liên tục một id bị lỗi
     * */
    fun onAdFailedToLoad(adId: String?) {
        if (adId != null) {
            mAdLoadFailedTimestamp[adId] = SystemClock.elapsedRealtime()
        }
    }

    /**
    * Khi Ads đã load thành công thì xóa flag failed
    * */
    fun onAdLoaded(adId: String?) {
        if (adId != null) {
            mAdLoadFailedTimestamp.remove(adId)
        }
    }

    /**
     * Kiểm tra xem AD id có thể load hay không, nếu id này đã load failed trước đó thì phải cách ít nhất WAITING_TIME_WHEN_LOAD_FAILED thì mới load lại
     */
    fun cantLoadId(adId: String?): Boolean {
        if (TextUtils.isEmpty(adId)) return true
        var timestamp: Long = 0
        if (mAdLoadFailedTimestamp.containsKey(adId)) {
            timestamp = mAdLoadFailedTimestamp[adId]!!
        }
        return SystemClock.elapsedRealtime() - timestamp < waitingTimeWhenLoadFailedInMs
    }

    fun parseAdsStateConfig(jsonData: String?): LinkedHashMap<String, Boolean> {
        if (!jsonData.isNullOrEmpty()) {
            try {
                // Parse config
                val states = LinkedHashMap<String, Boolean>()
                val jsonObject = JSONObject(jsonData)
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    states[key] = jsonObject.getBoolean(key)
                }
                return states
            } catch (e: Exception) {
                AdDebugLog.loge(e)
            }
        }
        return LinkedHashMap()
    }

    /**
     * jsonData is JSONObject, it will like this below
     * .
     * {
     * "banner_bottom" : true,
     * "native_bottom_main" : true,
     * "native_bottom_other" : true
     * }
     */
    fun setAdsEnableState(jsonData: String?): AdsConfig {
        try {
            if (!jsonData.isNullOrEmpty()) {
                // Save config to pref
                SharedPreference.setString(mApplication, ADS_ENABLE_STATE, jsonData)
                val states: Map<String, Boolean> = parseAdsStateConfig(jsonData)
                mAdsEnableState.clear()
                mAdsEnableState.putAll(states)
            }
        } catch (e: Exception) {
            AdDebugLog.loge(e)
        }
        return this@AdsConfig
    }

    fun _setWaitingTimeWhenLoadFailedInMs(timeInMs: Long): AdsConfig {
        waitingTimeWhenLoadFailedInMs = timeInMs
        SharedPreference.setLong(mApplication, WAITING_TIME_WHEN_LOAD_FAILED, timeInMs)
        return this@AdsConfig
    }

    fun _setTestMode(testMode: Boolean): AdsConfig {
        isTestMode = testMode
        return this@AdsConfig
    }

    fun _setTestGDPR(testGDPR: Boolean): AdsConfig {
        isTestGDPR = testGDPR
        return this@AdsConfig
    }

    fun _setShowLog(showLog: Boolean): AdsConfig {
        AdDebugLog.DEBUG_LOG = showLog
        return this@AdsConfig
    }

    fun _setFullVersion(fullVersion: Boolean): AdsConfig {
        isFullVersion = fullVersion
        return this@AdsConfig
    }

    fun _setCacheAd(cacheAd: Boolean): AdsConfig {
        this.isCacheAds = cacheAd
        SharedPreference.setLong(mApplication, FREQ_CAP_APP_OPEN_AD_IN_MS, freqAppOpenAdInMs)
        return this@AdsConfig
    }

    /*
     * InterOPA progress delay time
     * */
    fun setInterOPAProgressDelayInMs(time: Long): AdsConfig {
        if (mApplication != null) {
            SharedPreference.setLong(mApplication, INTER_OPA_PROGRESS_DELAY_IN_MS, time)
        }
        return this@AdsConfig
    }

    /*
     * InterOPA Splash delay time
     * */
    fun setInterOPASplashDelayInMs(time: Long): AdsConfig {
        if (mApplication != null) {
            SharedPreference.setLong(mApplication, INTER_OPA_SPLASH_DELAY_IN_MS, time)
        }
        return this@AdsConfig
    }

    /*
     * Freq time to show Interstitial Ads
     * */
    fun setFreqInterInMs(time: Long): AdsConfig {
        if (mApplication != null) {
            SharedPreference.setLong(mApplication, FREQ_CAP_INTER_IN_MS, time)
        }
        return this@AdsConfig
    }

    /*
     * Frequency time limited for OPA
     * */
    fun setFreqOPAInMs(time: Long): AdsConfig {
        if (mApplication != null) {
            SharedPreference.setLong(mApplication, FREQ_INTER_OPA_IN_MILLISECONDS, time)
        }
        return this@AdsConfig
    }

    /*
     * Last time OPA showed
     * */
    fun saveInterOPAShowedTimestamp(): AdsConfig {
        if (mApplication != null) {
            SharedPreference.setLong(mApplication, INTERSTITIAL_OPA_SHOWED_TIMESTAMP, SystemClock.elapsedRealtime())
        }
        return this@AdsConfig
    }

    /*
     * Minimum time to show Interstitial Ads from open app time
     * */
    fun setInterMinimumShowTimeInMs(time: Long): AdsConfig {
        if (mApplication != null) {
            SharedPreference.setLong(mApplication, MINIMUM_TIME_SHOW_INTER_IN_MS, time)
        }
        return this@AdsConfig
    }

    /*
     *
     * */
    fun hasWindowFocus(): Boolean {
        return hasWindowFocus && !isHideAllAds
    }

    fun isHideAllAds(): Boolean {
        return isHideAllAds
    }

    fun isAdEnable(type: AdsType): Boolean {
        var isEnable = false
        if (mAdsEnableState.containsKey(type._value)) {
            val enable = mAdsEnableState[type._value]
            isEnable = enable != null && enable
        }
//        AdDebugLog.logd("AdsType ${type.value}: isAdEnable = $isEnable")
        return isEnable
    }

    /*
     * This method will check condition time with config FREQ_INTER_OPA_IN_MILLISECONDS
     *
     * return true if current time minus the latest time OPA displayed > FREQ_INTER_OPA_IN_MILLISECONDS has been set
     * */
    fun canShowOPA(): Boolean {
        val freqInterOPAInMilliseconds = SharedPreference.getLong(mApplication, FREQ_INTER_OPA_IN_MILLISECONDS, DEFAULT_FREQ_CAP_INTER_OPA_IN_MS)
        if (freqInterOPAInMilliseconds == 0L) {
            return true
        }
        val lastTimeOPAShow = SharedPreference.getLong(mApplication, INTERSTITIAL_OPA_SHOWED_TIMESTAMP, DEFAULT_FREQ_CAP_INTER_OPA_IN_MS)
        return SystemClock.elapsedRealtime() - lastTimeOPAShow >= freqInterOPAInMilliseconds
    }

    /*
    * Splash delay time
    * */
    val interOPASplashDelayInMs: Long
        get() = if (mApplication != null) {
            SharedPreference.getLong(mApplication, INTER_OPA_SPLASH_DELAY_IN_MS, DEFAULT_INTER_OPA_SPLASH_DELAY_IN_MS)
        } else DEFAULT_INTER_OPA_SPLASH_DELAY_IN_MS

    /*
    * Fake progress delay time
    * */
    val interOPAProgressDelayInMs: Long
        get() = if (mApplication != null) {
            SharedPreference.getLong(mApplication, INTER_OPA_PROGRESS_DELAY_IN_MS, DEFAULT_INTER_OPA_PROGRESS_DELAY_IN_MS)
        } else DEFAULT_INTER_OPA_PROGRESS_DELAY_IN_MS

    /*
     *
     * */
    fun saveInterstitialShowedTimestamp() {
        if (mApplication != null) {
            SharedPreference.setLong(mApplication, INTERSTITIAL_SHOWED_TIMESTAMP, SystemClock.elapsedRealtime())
        }
    }

    private val interstitialShowedTimestamp: Long
        get() = if (mApplication != null) {
            SharedPreference.getLong(mApplication, INTERSTITIAL_SHOWED_TIMESTAMP, 0L)
        } else 0L

    fun canShowInterstitial(): Boolean {
        if (mApplication != null) {
            val timePassed = SystemClock.elapsedRealtime() - interstitialShowedTimestamp
            return timePassed >= SharedPreference.getLong(mApplication, FREQ_CAP_INTER_IN_MS, DEFAULT_FREQ_CAP_INTER_IN_MS)
        }
        return false
    }

    /*
     * AppOpenAd show when resume to foreground
     * */
    fun saveAppOpenAdShowedTimestamp() {
        if (mApplication != null) {
            SharedPreference.setLong(mApplication, APP_OPEN_AD_SHOWED_TIMESTAMP, SystemClock.elapsedRealtime())
        }
    }

    private val appOpenAdShowedTimestamp: Long
        get() = if (mApplication != null) {
            SharedPreference.getLong(mApplication, APP_OPEN_AD_SHOWED_TIMESTAMP, 0L)
        } else 0L

    fun canShowAppOpenAd(): Boolean {
        if (mApplication != null) {
            val timePassed = SystemClock.elapsedRealtime() - appOpenAdShowedTimestamp
            return timePassed >= freqAppOpenAdInMs
        }
        return false
    }
}