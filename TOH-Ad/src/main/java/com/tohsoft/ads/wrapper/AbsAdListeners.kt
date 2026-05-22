package com.tohsoft.ads.wrapper

abstract class AbsAdListeners {

    open var mAdListeners: HashMap<String, AdWrapperListener?> = hashMapOf()

    fun addListener(listener: AdWrapperListener?) {
        listener?.let {
            val key = it.hashCode().toString()
            mAdListeners[key] = it
        }

        removeNullListeners()
    }

    fun removeListener(listener: AdWrapperListener?) {
        listener?.let {
            val key = it.hashCode().toString()
            if (mAdListeners.containsKey(key)) {
                mAdListeners.remove(key)
            }
        }
    }

    private fun removeNullListeners() {
        try {
            val removeKeys: MutableList<String> = ArrayList()
            for ((key, value) in mAdListeners) {
                if (value == null) {
                    removeKeys.add(key)
                }
            }
            for (key in removeKeys) {
                mAdListeners.remove(key)
            }
        } catch (ignored: Exception) {
        }
    }

    /**
     * AdListeners
     * */
    open fun notifyAdLoaded() {
        mAdListeners.values.forEach { listener ->
            listener?.onAdLoaded()
        }
    }

    open fun notifyAdLoadFailed(errorCode: Int = -101, message: String?) {
        mAdListeners.values.forEach { listener ->
            listener?.onAdFailedToLoad(errorCode, message)
        }
    }

    open fun notifyAdOpened() {
        mAdListeners.values.forEach { listener ->
            listener?.onAdOpened()
        }
    }

    open fun notifyAdClicked() {
        mAdListeners.values.forEach { listener ->
            listener?.onAdClicked()
        }
    }

    open fun notifyAdClosed() {
        mAdListeners.values.forEach { listener ->
            listener?.onAdClosed()
        }
    }

    open fun notifyAdStartLoad() {
        try {
            mAdListeners.values.forEach { listener ->
                listener?.onAdStartLoad()
            }
        } catch (_: Exception) {
        }
    }

    open fun notifyAdImpression() {
        mAdListeners.values.forEach { listener ->
            listener?.onAdImpression()
        }
    }

    open fun notifyPaidEvent(adValue: com.google.android.gms.ads.AdValue, adUnitId: String?, adSource: String?) {
        mAdListeners.values.forEach { listener ->
            listener?.onPaidEvent(adValue, adUnitId, adSource)
        }
    }

}