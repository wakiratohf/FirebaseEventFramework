package com.tohsoft.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.text.TextUtils
import android.view.ViewGroup
import com.google.android.gms.ads.MobileAds
import com.tohsoft.ads.models.AdsId
import com.tohsoft.ads.models.AdsType
import com.tohsoft.ads.models.BannerType
import com.tohsoft.ads.models.LoadingState
import com.tohsoft.ads.utils.AdDebugLog
import com.tohsoft.ads.utils.AdsUtils
import com.tohsoft.ads.utils.SharedPreference
import com.tohsoft.ads.wrapper.AdOPAListener
import com.tohsoft.ads.wrapper.AdViewWrapper
import com.tohsoft.ads.wrapper.AdWrapper
import com.tohsoft.ads.wrapper.AdWrapperListener
import com.tohsoft.ads.wrapper.AppOpenAdsHelper
import com.tohsoft.ads.wrapper.InterstitialAdWrapper
import com.tohsoft.ads.wrapper.InterstitialOPA
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Arrays
import java.util.Locale

class AdsModule private constructor() {

    companion object {
        private const val GENERAL_CONFIG_ADS_ID_LIST: String = "ads_id_list"
        private const val CUSTOM_CONFIG_ADS_ID_LIST: String = "custom_ads_id_list"
        private const val DEFAULT_ASSETS_ADMOB_FILE_NAME: String = "admob_ids.json"
        private const val DEFAULT_ASSETS_FAN_FILE_NAME: String = "fan_ids.json"

        private const val DEFAULT_ADS_ID_LIST = "ADMOB-0"

        @JvmStatic
        private val sInstance: AdsModule by lazy { AdsModule() }

        @JvmStatic
        fun getInstance(): AdsModule {
            return sInstance
        }
    }

    private var mApplication: Application? = null

    // Banner
    var mBannerBottom: AdViewWrapper? = null
    var mBannerEmptyScreen: AdViewWrapper? = null
    var mBannerExitDialog: AdViewWrapper? = null

    // Interstitial
    var mInterstitialOPA: InterstitialOPA? = null
    private var mInterstitialAds: InterstitialAdWrapper? = null

    var mAppOpenAd: AppOpenAdsHelper? = null

    private var mLoadingState = LoadingState.NONE
    private var mSession = 0

    private var mAdsIdConfigList: ArrayList<String> = arrayListOf()
    private val mCustomAdsIdConfig: HashMap<AdsType, List<String>> = hashMapOf()
    private var mAdmobAdsId: AdsId? = null
    private var mFanAdsId: AdsId? = null
    private var mAdsId: AdsId? = null

    val context: Context?
        get() = mApplication

    fun mustInit(): Boolean {
        return mApplication == null || mAdsId == null
    }

    fun resetInitState() {
        mLoadingState = LoadingState.NONE
    }

    fun setSession(session: Int) {
        mSession = session
    }

    private fun initializeCompleted(): Boolean {
        return mLoadingState == LoadingState.FINISHED
    }

    /**
     * Set Application Context & initialize modules
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun init(application: Application, callback: InitCallback? = null): AdsModule {
        try {
            mApplication = application
            if (mLoadingState == LoadingState.NONE) {
                mLoadingState = LoadingState.LOADING
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val start = SystemClock.elapsedRealtime()
                        MobileAds.initialize(application) {
                            mLoadingState = LoadingState.FINISHED
                            AdDebugLog.loge("MobileAds initializationCompleted -> Take " + (SystemClock.elapsedRealtime() - start) + " ms")
                            GlobalScope.launch(Dispatchers.Main) {
                                callback?.onInitializeCompleted()
                            }
                        }
                        MobileAds.setAppMuted(true)
                        MobileAds.setAppVolume(0.0f)
                    } catch (e: Exception) {
                        AdDebugLog.loge(e)
                    }
                }
            }
            AdsConfig.getInstance().initAdsState(application)

            if (mAdsIdConfigList.isEmpty()) {
                initResources(application)
            }

            if (initializeCompleted()) {
                callback?.onInitializeCompleted()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return this@AdsModule
    }

    /**
     * Set Application Context
     */
    fun setApplication(application: Application): AdsModule {
        try {
            if (mApplication == null) {
                mApplication = application
            }
            if (mAdsId == null) {
                initResources(application)
            }
        } catch (_: Exception) {
        }
        return this@AdsModule
    }

    /**
     * Set Application Context & initialize resources AdsId if needed
     * */
    fun initResources(application: Application): AdsModule {
        mApplication = application
        if (mAdsIdConfigList.isEmpty() || mAdsId == null) {
            mAdsIdConfigList.clear()
            mAdsIdConfigList.addAll(generateAdsIdListConfig(SharedPreference.getString(mApplication, GENERAL_CONFIG_ADS_ID_LIST, DEFAULT_ADS_ID_LIST)))
            setResourceAdsId(DEFAULT_ASSETS_ADMOB_FILE_NAME, DEFAULT_ASSETS_FAN_FILE_NAME)
        }
        return this@AdsModule
    }

    /**
     * Khi Activity bị mất focus (inactive), VD như show dialog thì sẽ ẩn hết Ads hiển thị ở UI đi, trừ Ads show ở dialog
     */
    fun onWindowFocusChanged() {
        val ads: List<AdWrapper?> = listOf(mBannerBottom, mBannerEmptyScreen)
        if (AdsConfig.getInstance().hasWindowFocus()) {
            ads.forEach { adWrapper -> adWrapper?.visibleAds() }
        } else {
            ads.forEach { adWrapper -> adWrapper?.invisibleAds() }
        }
    }

    /**
     * Ẩn hết các loại Ads khác trong UI, bao gồm cả Ads show ở dialog trong app, trừ Ads ở dialog Ask lock new app vì dialog này sẽ overlay lên toàn bộ UI khác
     * Ignore mBannerAskLock
     */
    fun hideAllAds() {
        val ads: List<AdWrapper?> = listOf(mBannerBottom, mBannerEmptyScreen, mBannerExitDialog)
        if (AdsConfig.getInstance().isHideAllAds()) {
            ads.forEach { adWrapper -> adWrapper?.visibleAds() }
        } else {
            ads.forEach { adWrapper -> adWrapper?.invisibleAds() }
        }
    }

    /*
     * Set and generate Ads id from assets file
     * */
    fun setResourceAdsId(admobAssetsFileName: String?, fanAssetsFileName: String?): AdsModule {
        try {
            var shouldRefresh = false
            if (!TextUtils.isEmpty(admobAssetsFileName) && (!TextUtils.equals(admobAssetsFileName, DEFAULT_ASSETS_ADMOB_FILE_NAME) || mAdmobAdsId == null)) {
                mAdmobAdsId = AdsUtils.readIdsFromAssetsFile(mApplication, admobAssetsFileName)
                shouldRefresh = mAdmobAdsId != null
            }
            if (!TextUtils.isEmpty(fanAssetsFileName) && (!TextUtils.equals(fanAssetsFileName, DEFAULT_ASSETS_FAN_FILE_NAME) || mFanAdsId == null)) {
                mFanAdsId = AdsUtils.readIdsFromAssetsFile(mApplication, fanAssetsFileName)
                // Dùng OR để giữ lại true nếu admob đã load thành công trước đó
                shouldRefresh = shouldRefresh || mFanAdsId != null
            }
            if (shouldRefresh && mAdsIdConfigList.isNotEmpty() && (mAdmobAdsId != null || mFanAdsId != null)) {
                mAdsId = AdsUtils.mixAdsIdWithConfig(mAdmobAdsId, mFanAdsId, mAdsIdConfigList)
                refreshAdsIdIfNeeded()
            }

            // Generate custom Ad id list if needed
            setCustomAdsIdListConfig(SharedPreference.getString(mApplication, CUSTOM_CONFIG_ADS_ID_LIST, ""))
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return this@AdsModule
    }

    /*
     * This method must be called before setCustomAdsIdListConfig() is called
     * */
    fun setAdsIdListConfig(adsIdListConfig: String): AdsModule {
        try {
//            AdDebugLog.logd("\n---------------\nadsIdListConfig: " + adsIdListConfig + "\n---------------");
            if (!TextUtils.isEmpty(adsIdListConfig) && !TextUtils.equals(
                    SharedPreference.getString(mApplication, GENERAL_CONFIG_ADS_ID_LIST, ""),
                    adsIdListConfig
                )
            ) {
                SharedPreference.setString(mApplication, GENERAL_CONFIG_ADS_ID_LIST, adsIdListConfig)
                mAdsIdConfigList.clear()
                mAdsIdConfigList.addAll(generateAdsIdListConfig(adsIdListConfig))

                if (mAdmobAdsId == null && mFanAdsId == null) {
                    setResourceAdsId(DEFAULT_ASSETS_ADMOB_FILE_NAME, DEFAULT_ASSETS_FAN_FILE_NAME)
                }
                if (mAdmobAdsId != null || mFanAdsId != null) {
                    mAdsId = AdsUtils.mixAdsIdWithConfig(mAdmobAdsId, mFanAdsId, mAdsIdConfigList)
                    refreshAdsIdIfNeeded()
                }

                // Generate custom Ad id list if needed
                setCustomAdsIdListConfig(SharedPreference.getString(mApplication, CUSTOM_CONFIG_ADS_ID_LIST, ""))
            }
        } catch (e: java.lang.Exception) {
            AdDebugLog.loge(e)
        }
        return this@AdsModule
    }

    /*
     * This method must call after setAdsIdListConfig() is called
     * */
    fun setCustomAdsIdListConfig(customAdsIdJsonValue: String?): AdsModule {
        try {
            if (!TextUtils.isEmpty(customAdsIdJsonValue)) {
                AdDebugLog.logd("\n---------------\ncustomAdsIdJsonValue:\n$customAdsIdJsonValue\n---------------")
                SharedPreference.setString(mApplication, CUSTOM_CONFIG_ADS_ID_LIST, customAdsIdJsonValue)

                generateCustomAdsIdList(customAdsIdJsonValue)
                mixCustomAdsIdList()
            }
        } catch (e: java.lang.Exception) {
            AdDebugLog.loge(e)
        }
        return this@AdsModule
    }

    /*
     * Parse custom Ads id from JSONObject
     * (customAdsIdJsonValue will look like: { 	"banner_in_app" : "ADMOB-0,ADMOB-1"  })
     * */
    private fun generateCustomAdsIdList(customAdsIdJsonValue: String?) {
        try {
            if (customAdsIdJsonValue.isNullOrEmpty()) return

            val jsonObject = JSONObject(customAdsIdJsonValue)
            val iterator = jsonObject.keys()
            mCustomAdsIdConfig.clear()

            while (iterator.hasNext()) {
                val key = iterator.next()
                var adsType: AdsType? = null
                if (key.lowercase(Locale.getDefault()).startsWith(AdsType.BANNER_BOTTOM._value)) {
                    adsType = AdsType.BANNER_BOTTOM
                } else if (key.lowercase(Locale.getDefault()).startsWith(AdsType.BANNER_EXIT_DIALOG._value)) {
                    adsType = AdsType.BANNER_EXIT_DIALOG
                } else if (key.lowercase(Locale.getDefault()).startsWith(AdsType.BANNER_EMPTY_SCREEN._value)) {
                    adsType = AdsType.BANNER_EMPTY_SCREEN
                } else if (key.lowercase(Locale.getDefault()).startsWith(AdsType.INTERSTITIAL._value)) {
                    adsType = AdsType.INTERSTITIAL
                } else if (key.lowercase(Locale.getDefault()).startsWith(AdsType.INTERSTITIAL_OPA._value)) {
                    adsType = AdsType.INTERSTITIAL_OPA
                } else if (key.lowercase(Locale.getDefault()).startsWith(AdsType.APP_OPEN_AD._value)) {
                    adsType = AdsType.APP_OPEN_AD
                }

                // Put custom Ads id to map
                if (adsType != null && generateAdsIdListConfig(jsonObject.getString(key)).isNotEmpty()) {
                    mCustomAdsIdConfig[adsType] = generateAdsIdListConfig(jsonObject.getString(key))
                }
            }
        } catch (_: java.lang.Exception) {
        }
    }

    /*
     * Mix Ads id with custom Ads id
     * */
    private fun mixCustomAdsIdList() {
        if (mAdsId != null && mCustomAdsIdConfig.isNotEmpty()) {
            for (adsType in mCustomAdsIdConfig.keys) {
                val adsIdConfigList: List<String>? = mCustomAdsIdConfig[adsType]
                if (adsIdConfigList != null) {
                    AdsUtils.mixCustomAdsIdConfig(mAdsId, mAdmobAdsId, mFanAdsId, adsType, adsIdConfigList)
                }
            }

            refreshAdsIdIfNeeded()
        }
    }

    /*
     * Check and set Ads id for existed wrapper
     * */
    private fun refreshAdsIdIfNeeded() {
        if (mAdsId != null) {
            mAdsId?.banner_bottom?.let {
                mBannerBottom?.setAdsId(it)
            }
            mAdsId?.banner_empty_screen?.let {
                mBannerEmptyScreen?.setAdsId(it)
            }
            mAdsId?.banner_exit_dialog?.let {
                mBannerExitDialog?.setAdsId(it)
            }
            mAdsId?.app_open_ad?.let {
                mAppOpenAd?.setAdsId(it)
            }
        }
    }

    /*
     * Generate Ads id list with a Firebase value
     * (adsIdList will look like: FAN-0, ADMOB-0, ADMOB-1)
     * */
    private fun generateAdsIdListConfig(adsIdList: String?): List<String> {
        if (adsIdList.isNullOrEmpty()) return emptyList()
        if (adsIdList.contains(",")) {
            val config = adsIdList.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            return listOf(*config)
        } else if (!TextUtils.isEmpty(adsIdList)) {
            val config = arrayOf(adsIdList)
            return Arrays.asList(*config)
        }
        return java.util.ArrayList()
    }

    private fun wrapHeightForContainer(container: ViewGroup?) {
        container?.let {
            // Set height cho container là WrapContent
            AdsUtils.setHeightForContainer(container, 0)
        }
    }

    /**
     * Generate standard bottom Banner - mBannerBottom static instance (Adaptive Banner)
     */
    fun showBannerBottom(context: Context, container: ViewGroup?) {
        AdDebugLog.logd("showBannerBottom, mLoadingState: $mLoadingState, " +
                "banner_bottom: ${mAdsId?.banner_bottom}, " +
                "canShowAd: ${AdsConfig.getInstance().canShowAd()}, " +
                "isAdEnable: ${AdsConfig.getInstance().isAdEnable(AdsType.BANNER_BOTTOM)}")
        if (mLoadingState != LoadingState.NONE
            && mAdsId?.banner_bottom != null
            && AdsConfig.getInstance().canShowAd()
            && AdsConfig.getInstance().isAdEnable(AdsType.BANNER_BOTTOM)) {
            if (mBannerBottom == null) {
                mBannerBottom = AdViewWrapper(context, mAdsId!!.banner_bottom!!, BannerType.BOTTOM)
            }
            mBannerBottom!!.showBottomBanner(container)
        } else container?.removeAllViews()
    }

    // Banner exit dialog
    fun showBannerExitDialog(container: ViewGroup?) {
        getBannerExitDialog(mApplication)?.showMediumBanner(container) ?: let { container?.removeAllViews() }
    }

    fun getBannerExitDialog(context: Context?): AdViewWrapper? {
        /*if (AdsConfig.getInstance().canShowAd()
            && AdsConfig.getInstance().isAdEnable(AdsType.BANNER_EXIT_DIALOG)
            && !mAdsId?.banner_exit_dialog.isNullOrEmpty()
        ) {
            context?.let {
                if (mBannerExitDialog == null) {
                    mBannerExitDialog = AdViewWrapper(it, mAdsId!!.banner_exit_dialog!!, BannerType.EXIT_DIALOG)
                }
            }
        }*/
        return mBannerExitDialog
    }

    // Banner empty screen
    fun showBannerEmptyScreen(container: ViewGroup?) {
        getBannerEmptyScreen(mApplication)?.showMediumBanner(container) ?: let { container?.removeAllViews() }
    }

    private fun getBannerEmptyScreen(context: Context?): AdViewWrapper? {
        /*if (AdsConfig.getInstance().canShowAd()
            && AdsConfig.getInstance().isAdEnable(AdsType.BANNER_EMPTY_SCREEN)
            && !mAdsId?.banner_empty_screen.isNullOrEmpty()
        ) {
            context?.let {
                if (mBannerEmptyScreen == null) {
                    mBannerEmptyScreen = AdViewWrapper(it, mAdsId!!.banner_empty_screen!!, BannerType.EMPTY_SCREEN)
                }
            }
        }*/
        return mBannerEmptyScreen
    }

    /**
     * Interstitial
     * */
    fun getInterstitialAds(activity: Activity?): InterstitialAdWrapper? {
        /*if (AdsConfig.getInstance().canShowAd()
            && !mAdsId?.interstitial.isNullOrEmpty()
            && AdsConfig.getInstance().isAdEnable(AdsType.INTERSTITIAL)
        ) {
            if (mInterstitialAds == null) {
                mInterstitialAds = InterstitialAdWrapper(activity!!, mAdsId!!.interstitial!!)
            }
        }*/
        return mInterstitialAds
    }

    /**
     * Interstitial OPA
     * */
    fun getInterstitialOPA(context: Context, opaListener: AdOPAListener? = null, adListener: AdWrapperListener? = null): InterstitialOPA? {
       /* if (AdsConfig.getInstance().canShowAd()
            && AdsConfig.getInstance().isAdEnable(AdsType.INTERSTITIAL_OPA)
            && !mAdsId?.interstitial_opa.isNullOrEmpty()
        ) {
            if (mInterstitialOPA == null) {
                mInterstitialOPA = InterstitialOPA(context, mAdsId!!.interstitial_opa!!)
            }
            opaListener?.let { mInterstitialOPA?.mOPAListener = opaListener }
            mInterstitialOPA?.addListener(adListener)
            return mInterstitialOPA
        }*/
        return null
    }

    /**
     * AppOpenAds
     * */
    fun getAppOpenAd(context: Context, opaListener: AdOPAListener? = null, adListener: AdWrapperListener? = null): AppOpenAdsHelper? {
        /*if (AdsConfig.getInstance().canShowAd()
            && AdsConfig.getInstance().isAdEnable(AdsType.APP_OPEN_AD)
            && !mAdsId?.app_open_ad.isNullOrEmpty()
        ) {
            if (mAppOpenAd == null) {
                mAppOpenAd = AppOpenAdsHelper(context, mAdsId!!.app_open_ad!!)
            }
            opaListener?.let { mAppOpenAd?.mOPAListener = opaListener }
            mAppOpenAd?.addListener(adListener)
            return mAppOpenAd
        }*/
        return null
    }

    /**
     * Destroy
     * */
    private var ignoreDestroyAd = false

    fun destroyAllAds() {
        destroy(mSession)
        mInterstitialOPA?.destroy()
        mInterstitialOPA = null
        mAppOpenAd?.destroy()
        mAppOpenAd = null
    }

    fun ignoreDestroyAd(ignore: Boolean = true) {
        ignoreDestroyAd = ignore
    }

    fun destroy(session: Int) {
        if (ignoreDestroyAd) {
            ignoreDestroyAd = false
            return
        }
        if (mSession != session) {
            AdDebugLog.loge("RETURN destroyAds when mSession != session")
            return
        }
        AdDebugLog.loge(" -> Destroy Ad instances")
        mBannerBottom?.destroy()
        mBannerBottom = null
        mBannerEmptyScreen?.destroy()
        mBannerEmptyScreen = null
        mBannerExitDialog?.destroy()
        mBannerExitDialog = null
        mInterstitialAds?.destroy()
        mInterstitialAds = null
        mInterstitialOPA?.destroy()
        mInterstitialOPA = null
    }

    interface InitCallback {
        fun onInitializeCompleted()
    }
}
