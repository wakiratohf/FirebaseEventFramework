package com.tohsoft.ads.wrapper

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoController.VideoLifecycleCallbacks
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.tohsoft.ad.R
import com.tohsoft.ads.AdsConfig
import com.tohsoft.ads.AdsConstants
import com.tohsoft.ads.models.NativeAdType
import com.tohsoft.ads.utils.AdDebugLog
import com.tohsoft.ads.utils.AdsUtils
import com.tohsoft.ads.utils.CoroutineHandler
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
class NativeAdViewWrapper(context: Context, adIds: List<String>, nativeAdType: NativeAdType) : AdWrapper(context, adIds) {
    init {
        TAG = "[${this::class.java.simpleName}] ${hashCode()} -- "
    }

    private var mNativeAd: NativeAd? = null
    private var mNativeAdView: NativeAdView? = null
    private var mLayoutType = nativeAdType
    private var mJob: Job? = null

    private fun canVisibleNativeAd(): Boolean {
        return AdsConfig.getInstance().hasWindowFocus() || (mLayoutType == NativeAdType.DIALOG && !AdsConfig.getInstance().isHideAllAds())
    }

    override fun visibleAds() {
        if (canVisibleNativeAd()) {
            mNativeAdView?.visibility = View.VISIBLE
        }
    }

    override fun invisibleAds() {
        if (mLayoutType != NativeAdType.DIALOG || AdsConfig.getInstance().isHideAllAds()) {
            mNativeAdView?.visibility = View.INVISIBLE
        }
    }

    fun setLayoutType(type: NativeAdType) {
        this.mLayoutType = type
    }

    override fun removeAdsFromContainer() {
        mAdsContainer?.let { container ->
            if (container.childCount > 0) {
                val nativeAdView = container.getChildAt(0)
                if (nativeAdView is NativeAdView) {
                    nativeAdView.destroy()
                }
            }
            container.removeAllViews()
        }
    }

    val layout: Int
        get() = if (mLayoutType == NativeAdType.SMALL) {
            R.layout.native_ad_bottom
        } else if (mLayoutType == NativeAdType.MEDIUM) {
            R.layout.native_ad_medium
        } else if (mLayoutType == NativeAdType.DIALOG) {
            R.layout.native_ad_dialog
        } else {
            R.layout.native_ad_medium
        }

    fun removeContainer(viewGroup: ViewGroup) {
        val currentContainer = mAdsContainer
        if (currentContainer != null && currentContainer.hashCode() == viewGroup.hashCode()) {
            removeAdsFromContainer()
            mAdsContainer = null
        }
    }

    override fun getAdId(): String {
        return if (AdsConfig.getInstance().isTestMode) {
            mCurrentAdsId = AdsConstants.native_ad_test_id
            AdsConstants.native_ad_test_id
        } else {
            super.getAdId()
        }
    }

    /**
     * Creates a request for a new native ad based on the boolean parameters and calls the
     * corresponding "populate" method when one is successfully returned.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("InflateParams")
    fun showAds(context: Context?, viewGroup: ViewGroup?) {
        if (AdsConfig.getInstance().isFullVersion || context == null) {
            return
        }
        val appContext = context.applicationContext

        // Process container
        viewGroup?.let {
            // Remove Ads from current container
            removeAdsFromContainer()
            // Keep current container instance need to show Ads
            updateContainer(viewGroup)
        }

        // Get Ads id
        val adId = getAdId()

        // Check conditions
        if (!checkConditions()) return

        // Check network state
        if (!AdsUtils.isInternetAvailable()) {
            AdDebugLog.loge("$TAG RETURN when no network connected\nid: $mCurrentAdsId")
            return
        }

        // Mark flags
        isLoading = true
        isLoaded = false
        // Notify Ad start load
        notifyAdStartLoad()
        // Show loading
        showLoadingView(container = viewGroup)
        // Cancel previous loading Ads
        cancelLoadAd()
        // Start load Ads
        mJob = SupervisorJob()
        GlobalScope.launch(CoroutineHandler.IOScope + mJob!!) {
            val builder = AdLoader.Builder(appContext, adId) // Must generate AdLoader.Builder in IO thread, because it can make ANR
            if (isActive) {
                // Start load Ads
                startLoadAds(appContext, adId, builder)
            }
        }
    }

    private fun reloadWhenAdsClicked(context: Context) {
        // Set flags
        isLoading = false
        isLoaded = false
        loadedTimestamp = 0
        // Remove Ads from container
        removeAdsFromContainer()
        // Delay to reload Ads
        mHandler.postDelayed({
            if (!isLoading) {
                destroyAdsInstance()
                showAds(context, mAdsContainer)
            }
        }, 1000)
    }

    private fun destroyAdsInstance() {
        if (mNativeAd != null) {
            mNativeAd!!.destroy()
            mNativeAd = null
        }
    }

    private fun startLoadAds(context: Context, adsId: String, builder: AdLoader.Builder) {
        mHandler.post { showLoadingView(getContainer()) }

        if (AdsConfig.getInstance().cantLoadId(adsId)) {
            AdDebugLog.loge("$TAG RETURN because this id just failed to load, waitingTimeWhenLoadFailedInMs = ${AdsConfig.getInstance().waitingTimeWhenLoadFailedInMs} id: $adsId ")
            mHandler.post { onAdFailedToLoad(context, -101, "Ads just failed to load") }
            return
        }

        builder.forNativeAd { nativeAd: NativeAd -> onAdLoaded(nativeAd, adsId) }
        builder.withNativeAdOptions(nativeAdOptions)
        builder.withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                AdDebugLog.loge("$TAG ${loadAdError.message}")
                // Mark flag for this id just load failed
                AdsConfig.getInstance().onAdFailedToLoad(adsId)
                // Notify load failed or retry load Ads
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    onAdFailedToLoad(context, loadAdError.code, loadAdError.message)
                } else {
                    mHandler.post { onAdFailedToLoad(context, loadAdError.code, loadAdError.message) }
                }
            }

            override fun onAdClicked() {
                super.onAdClicked()
                reloadWhenAdsClicked(context)
            }
        })

        AdDebugLog.logd("$TAG\nloadAd adsId: $adsId")
        val adLoader = builder.build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private val nativeAdOptions: NativeAdOptions
        get() {
            val videoOptions = VideoOptions.Builder().setStartMuted(true).build()
            return NativeAdOptions.Builder().setVideoOptions(videoOptions).build()
        }

    private fun onAdLoaded(nativeAd: NativeAd, adsId: String) {
        // Set flags
        isLoading = false
        isLoaded = true
        // Mark flag for this id just loaded
        AdsConfig.getInstance().onAdLoaded(adsId)
        // Inflate nativeAd
        mNativeAd = nativeAd
        showNativeAd()
        // Notify Ad loaded
        adLoaded()
    }

    private fun onAdFailedToLoad(context: Context, errorCode: Int, errorMsg: String?) {
        if (canRetryLoadAdWhenLoadFailed()) {
            // remove ad from container and delete ad instance
            removeAdsFromContainer()
            // Get next ad id in list
            getNextAdsId()
            // Post delay to avoid ANR
            val delayTime: Long = (mAdsPosition + 1) * 2_000L
            mHandler.postDelayed({
                isLoading = false
                showAds(context, mAdsContainer)
            }, delayTime)
        } else {
            // Hide loading view
            hideLoadingView()
            // Reset ad position
            resetAdPosition()
            // Notify load failed
            adLoadFailed(errorCode, errorMsg)
        }
    }

    private fun cancelLoadAd() {
        if (mJob?.isActive == true) {
            mJob?.cancel()
        }
        mJob = null
    }

    override fun addAdsToContainer() {
        showNativeAd()
    }

    private fun showNativeAd() {
        mNativeAd?.let {
            hideLoadingView(removeImmediate = true)

            val container = mAdsContainer
            AdDebugLog.logd("$TAG showAds with layoutType = $mLayoutType container: ${container?.hashCode() ?: "NULL"}")
            if (container != null && container.context != null) {
                val context = container.context
                // Set MATCH_PARENT for container
                validateWidthForContainer(container)

                val nativeAdView: NativeAdView
                if (container.childCount > 0 && container.getChildAt(0) is NativeAdView) {
                    nativeAdView = container.getChildAt(0) as NativeAdView
                } else {
                    // Create a NativeAdView with a container context and set it as the parent of the NativeAdView
                    // (to ensure the NativeAdView will be destroyed when the container is destroyed)
                    nativeAdView = LayoutInflater.from(context).inflate(layout, container, false) as NativeAdView

                    // Add NativeAdView to container
                    container.removeAllViews()
                    container.addView(nativeAdView)
                }

                // Set visible for container (ignore type LIST_AUDIO & LIST_VIDEO)
                container.visibility = View.VISIBLE
                // Listen to the container detach event to remove NativeAdView from the container
                setupAdContainerAttachStateListener(container)

                mNativeAdView = nativeAdView

                mNativeAd?.let {
                    // Fill NativeAd data for NativeAdView
                    populateNativeAdView(it, nativeAdView)
                }
            }

            if (canVisibleNativeAd()) {
                visibleAds()
            } else {
                invisibleAds()
            }
        }
    }

    private fun validateWidthForContainer(container: ViewGroup) {
        val layoutParams = container.layoutParams
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        container.layoutParams = layoutParams
    }

    /**
     * Populates a [NativeAd] object with data from a given
     * [NativeAd].
     *
     * @param nativeAd     the object containing the ad's assets
     * @param nativeAdView the view to be populated
     */
    private fun populateNativeAdView(nativeAd: NativeAd, nativeAdView: NativeAdView) {
        try {
            // Set the media view. Media content will be automatically populated in the media view once
            // adView.setNativeAd() is called.
            val mediaView: MediaView? = nativeAdView.findViewById(R.id.ad_media)
            mediaView?.let { nativeAdView.mediaView = mediaView }

            // Set other ad assets.
            nativeAdView.headlineView = nativeAdView.findViewById(R.id.ad_headline)
//            nativeAdView.bodyView = nativeAdView.findViewById(R.id.ad_body)
            nativeAdView.callToActionView = nativeAdView.findViewById(R.id.ad_call_to_action)
            nativeAdView.iconView = nativeAdView.findViewById(R.id.ad_app_icon)
//            nativeAdView.priceView = nativeAdView.findViewById(R.id.ad_price)
            nativeAdView.starRatingView = nativeAdView.findViewById(R.id.ad_stars)
            nativeAdView.storeView = nativeAdView.findViewById(R.id.ad_store)
            nativeAdView.advertiserView = nativeAdView.findViewById(R.id.ad_advertiser)
            setStyleForNativeAdView(nativeAdView)

            // The headline is guaranteed to be in every UnifiedNativeAd.
            if (nativeAdView.headlineView != null) {
                (nativeAdView.headlineView as TextView?)?.text = nativeAd.headline
            }

            // These assets aren't guaranteed to be in every UnifiedNativeAd, so it's important to
            // check before trying to display them.
            nativeAdView.bodyView?.let { bodyView ->
                if (nativeAd.body == null) {
                    bodyView.visibility = View.INVISIBLE
                } else {
                    bodyView.visibility = View.VISIBLE
                    (bodyView as TextView?)?.text = nativeAd.body
                }
            }
            // CTA button
            nativeAdView.callToActionView?.let { callToActionView ->
                if (nativeAd.callToAction == null) {
                    callToActionView.visibility = View.INVISIBLE
                } else {
                    callToActionView.visibility = View.VISIBLE
                    if (callToActionView is Button) {
                        callToActionView.text = nativeAd.callToAction
                    } else if (callToActionView is TextView) {
                        callToActionView.text = nativeAd.callToAction
                    }
                }
            }

            // These assets aren't guaranteed to be in every UnifiedNativeAd, so it's important to
            // check before trying to display them.
            nativeAdView.iconView?.let { iconView ->
                if (nativeAd.icon == null) {
                    iconView.visibility = View.GONE
                } else {
                    iconView.visibility = View.VISIBLE
                    (iconView as ImageView).setImageDrawable(nativeAd.icon!!.drawable)
                }
            }
            // Price
            nativeAdView.priceView?.let { priceView ->
                if (nativeAd.price == null) {
                    priceView.visibility = View.INVISIBLE
                } else {
                    priceView.visibility = View.VISIBLE
                    (priceView as TextView).text = nativeAd.price
                }
            }
            // Store view info
            nativeAdView.storeView?.let { storeView ->
                if (nativeAd.store == null) {
                    storeView.visibility = View.INVISIBLE
                } else {
                    storeView.visibility = View.VISIBLE
                    (storeView as TextView).text = nativeAd.store
                }
            }
            // Advertiser
            if (nativeAd.advertiser == null) {
                nativeAdView.advertiserView?.visibility = View.INVISIBLE
            } else {
                nativeAdView.advertiserView?.let { advertiserView ->
                    advertiserView.visibility = View.VISIBLE
                    (advertiserView as TextView).text = nativeAd.advertiser
                    // Gone storeView with type = SMALL
                    if (mLayoutType == NativeAdType.SMALL) {
                        nativeAdView.storeView?.visibility = View.GONE
                    }
                }

            }
            // App rating
            if (nativeAd.starRating == null) {
                nativeAdView.starRatingView?.visibility = View.INVISIBLE
            } else {
                nativeAdView.starRatingView?.let { starRatingView ->
                    (starRatingView as RatingBar).rating = nativeAd.starRating!!.toFloat()
                    starRatingView.setVisibility(View.VISIBLE)
                    if (mLayoutType == NativeAdType.SMALL) {
                        nativeAdView.storeView?.visibility = View.GONE
                        nativeAdView.advertiserView?.visibility = View.GONE
                    }
                }
            }

            // This method tells the Google Mobile Ads SDK that you have finished populating your
            // native ad view with this native ad. The SDK will populate the adView's MediaView
            // with the media content from this native ad.
            nativeAdView.setNativeAd(nativeAd)

            mediaView?.let {
                // Get the video controller for the ad. One will always be provided, even if the ad doesn't
                // have a video asset.
                if (nativeAd.mediaContent != null && nativeAd.mediaContent!!.hasVideoContent()) {
                    val videoController = nativeAd.mediaContent!!.videoController
                    videoController.mute(true)
                    // Create a new VideoLifecycleCallbacks object and pass it to the VideoController. The
                    // VideoController will call methods on this object when events occur in the video
                    // lifecycle.
                    videoController.videoLifecycleCallbacks = object : VideoLifecycleCallbacks() {
                        override fun onVideoEnd() {
                            // Publishers should allow native ads to complete video playback before refreshing
                            // or replacing them with another ad in the same UI location.
                            super.onVideoEnd()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AdDebugLog.loge(e)
        }
    }

    private fun setStyleForNativeAdView(adView: NativeAdView) {
        try {
            // Set color for CallToAction btn
            if (adView.callToActionView != null) {
                adView.callToActionView!!.backgroundTintList = ColorStateList.valueOf(AdsConfig.getInstance().accentColor)
            }
            // Set text color
            if (AdsConfig.getInstance().secondaryTextColor != -1) {
                val adBody = adView.findViewById<TextView>(R.id.ad_body)
                adBody?.setTextColor(AdsConfig.getInstance().secondaryTextColor)
            }
            if (AdsConfig.getInstance().primaryTextColor != -1) {
                val adHeadline = adView.findViewById<TextView>(R.id.ad_headline)
                adHeadline?.setTextColor(AdsConfig.getInstance().primaryTextColor)
            }
            // Set background
            /*if (AdsConfig.getInstance().nativeAdsBackgroundColor != -1) {
                val background = adView.findViewById<View>(R.id.native_ad_background)
                if (background != null && background.background == null) {
                    background.setBackgroundColor(AdsConfig.getInstance().nativeAdsBackgroundColor)
                }
            }*/
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private val listener: View.OnAttachStateChangeListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
        }

        override fun onViewDetachedFromWindow(view: View) {
            if (view is ViewGroup && view.childCount > 0) {
                val nativeAdView = view.getChildAt(0)
                if (nativeAdView is NativeAdView) {
                    nativeAdView.destroy()
                }
                view.removeAllViews()
            }
            view.removeOnAttachStateChangeListener(this)
        }
    }

    private fun setupAdContainerAttachStateListener(container: View?) {
        if (container == null) {
            return
        }
        container.removeOnAttachStateChangeListener(listener)
        container.addOnAttachStateChangeListener(listener)
    }

    override fun destroyAdInstance() {
        mNativeAd?.destroy()
        mNativeAd = null
        mNativeAdView?.destroy()
        mNativeAdView = null
    }

    override fun destroy() {
        super.destroy()
        mAdsPosition = 0
        cancelLoadAd()
    }
}
