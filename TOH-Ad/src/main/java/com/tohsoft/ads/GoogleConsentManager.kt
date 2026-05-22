package com.tohsoft.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.tohsoft.ads.utils.AdsUtils
import com.tohsoft.ads.utils.SharedPreference

/**
 * The Google Mobile Ads SDK provides the User Messaging Platform (Google's IAB Certified consent
 * management platform) as one solution to capture consent for users in GDPR impacted countries.
 * This is an example and you can choose another consent management platform to capture consent.
 */
class GoogleConsentManager private constructor (context: Context) {
    companion object {
        private const val TAG = "GoogleConsentManager"
        private const val PREF_CONSENT_ACCEPTED = "pref_consent_accepted"
        const val TIMEOUT = 5_000L

        @JvmStatic
        var consentDebugGeography = ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA

        @Volatile
        private var instance: GoogleConsentManager? = null

        @JvmStatic
        fun getInstance(context: Context) = instance ?: synchronized(this) {
            instance ?: GoogleConsentManager(context).also { instance = it }
        }
    }

    private val appContext = context.applicationContext
    private val mConsentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)
    private var mConsentForm: ConsentForm? = null
    private var mLoadingStatus: LoadConsentStatus = LoadConsentStatus.NONE
    private val mListeners: HashSet<ConsentListener?> = hashSetOf()

    private val mHandler = Handler(Looper.getMainLooper())

    fun addCallback(callback: ConsentListener?) {
        callback?.let { mListeners.add(it) }
    }

    fun removeCallback(callback: ConsentListener) {
        mListeners.remove(callback)
    }

    /** Helper method to determine if the app can request ads. */
    fun canRequestAds(): Boolean {
        return mConsentInformation.canRequestAds()
    }

    /**
     * Helper method to fast determine consent status accepted or not
     *
     * @return true: if previously accepted or config ConsentForm is disable
     * @return false: otherwise
     * */
    fun isConsentStatusAccepted(): Boolean {
        return SharedPreference.getBoolean(appContext, PREF_CONSENT_ACCEPTED, false)
    }

    /**
     * Helper method to call the UMP SDK methods to request consent information and load/show a
     * consent form if necessary.
     */
    @JvmOverloads
    fun gatherConsent(activity: Activity, callback: ConsentListener? = null, timeout: Long = TIMEOUT) {
        // Add callback
        addCallback(callback)
        // Delay to send timeout for this callback
        callback?.let { delayTimeout(timeout, it.hashCode()) }
        // Check loading status
        if (mLoadingStatus == LoadConsentStatus.REQUESTING || mLoadingStatus == LoadConsentStatus.LOADING_FORM) {
            Log.e(TAG, "\nRETURN when LoadingStatus = $mLoadingStatus")
            return
        }

        // For testing purposes, you can force a DebugGeography of EEA or NOT_EEA.
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false)
        if (AdsConfig.getInstance().isTestGDPR) {
            Log.e(TAG, "\nconsentDebugGeography = $consentDebugGeography")
            val debugSettings = ConsentDebugSettings.Builder(appContext)
                .setDebugGeography(consentDebugGeography)
                .addTestDeviceHashedId(AdsUtils.getDeviceId(appContext))
                .build()
            params.setConsentDebugSettings(debugSettings)
        }

        // Delay 4s timeout to get consent status
        val appContext = activity.applicationContext
        mLoadingStatus = LoadConsentStatus.REQUESTING
        // requestConsentInfoUpdate
        mConsentInformation.requestConsentInfoUpdate(activity, params.build(), {
            Log.e(TAG, "\nconsentStatus = ${mConsentInformation.consentStatus}, canRequestAds: ${mConsentInformation.canRequestAds()}")
            if (mConsentInformation.consentStatus == ConsentInformation.ConsentStatus.NOT_REQUIRED
                || mConsentInformation.consentStatus == ConsentInformation.ConsentStatus.OBTAINED
            ) {
                /**
                 * If consentStatus = NOT_REQUIRED -> user not in GEOGRAPHY_EEA -> do not need show ConsentForm -> can show Ad immediate
                 * => Save consent status to pref before callback invoked -> to init & show OPA after consent status updated
                 * */
                SharedPreference.setBoolean(appContext, PREF_CONSENT_ACCEPTED, true)
            } else if (isRequireConsentForm()) {
                SharedPreference.setBoolean(appContext, PREF_CONSENT_ACCEPTED, false)
            }

            mLoadingStatus = LoadConsentStatus.UPDATED
            if (isRequireConsentForm()) { // If user in EEA -> start load Consent form to show
                if (mConsentForm != null) {
                    // If the ConsentForm already exist -> send result
                    Log.e(TAG, "ConsentForm is ready -> Show immediate")
                    mHandler.removeCallbacksAndMessages(null)
                    mLoadingStatus = LoadConsentStatus.COMPLETED
                    if (activity is AppCompatActivity && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        consentFormLoaded()
                    } else {
                        mHandler.postDelayed({ consentFormLoaded() }, 250)
                    }
                } else {
                    // If the ConsentForm is not exist -> start load Consent form to show
                    loadForm()
                }
            } else {
                mLoadingStatus = LoadConsentStatus.COMPLETED
                // If user not in EEA or previously accepted -> return result
                consentGatheringComplete(null)
            }
        }, { requestConsentError ->
            // Consent gathering failed.
            Log.e(TAG, "requestConsentError:\nErrorCode: ${requestConsentError.errorCode} \nErrorMsg: ${requestConsentError.message}")
            consentGatheringComplete(requestConsentError)
        })
    }

    fun loadStatusAndLoadFormIfNeeded(activity: FragmentActivity, callback: ConsentListener? = null) {
        if (canRequestAds() || mConsentForm != null || mLoadingStatus == LoadConsentStatus.LOADING_FORM) {
            Log.e(TAG, "\nRETURN preload GDPR when " + (if (canRequestAds()) "canRequestAds = true" else "mConsentForm is already to show!"))
            return
        }

        addCallback(callback)
        // For testing purposes, you can force a DebugGeography of EEA or NOT_EEA.
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false)
        if (AdsConfig.getInstance().isTestGDPR) {
            Log.e(TAG, "\nDEBUG consentDebugGeography = $consentDebugGeography")
            val debugSettings =
                ConsentDebugSettings.Builder(appContext).setDebugGeography(consentDebugGeography).addTestDeviceHashedId(AdsUtils.getDeviceId(appContext))
                    .build()
            params.setConsentDebugSettings(debugSettings)
        }

        val appContext = activity.applicationContext
        mConsentInformation.requestConsentInfoUpdate(activity, params.build(), {
            Log.e(TAG, "\nrequestConsentInfoUpdate result: \nconsentStatus = ${mConsentInformation.consentStatus} \ncanRequestAds: ${mConsentInformation.canRequestAds()}")
            if (mConsentInformation.consentStatus == ConsentInformation.ConsentStatus.NOT_REQUIRED
                || mConsentInformation.consentStatus == ConsentInformation.ConsentStatus.OBTAINED
            ) {
                /**
                 * If consentStatus = NOT_REQUIRED -> user not in GEOGRAPHY_EEA -> do not need show ConsentForm -> can show Ad immediate
                 * => Save consent status to pref before callback invoked -> to init & show OPA after consent status updated
                 * */
                SharedPreference.setBoolean(appContext, PREF_CONSENT_ACCEPTED, true)
            } else if (isRequireConsentForm()) {
                SharedPreference.setBoolean(appContext, PREF_CONSENT_ACCEPTED, false)
            }

            if (isRequireConsentForm()) {
                // If user in EEA -> start load Consent form to show
                if (mConsentForm == null && mLoadingStatus != LoadConsentStatus.LOADING_FORM) {
                    loadForm()
                }
            } else {
                consentGatheringComplete(null)
            }
        }, { formError ->
            // Consent gathering failed.
            consentGatheringComplete(formError)
        })
    }

    private fun consentFormLoaded() {
        mListeners.forEach {
            it?.consentFormLoaded(this)
        }
    }

    private fun consentGatheringComplete(error: FormError?) {
        mHandler.removeCallbacksAndMessages(null)
        mListeners.forEach {
            it?.consentGatheringComplete(error)
        }
    }

    private fun delayTimeout(timeout: Long = TIMEOUT, callbackHashCode: Int) {
        Log.i(TAG, "START delay timeout for instance callback $callbackHashCode")
        mHandler.postDelayed({
            Log.e(TAG, "Consent timeout for instance callback $callbackHashCode")
            mListeners.find { it != null && it.hashCode() == callbackHashCode }?.consentTimeout()
        }, timeout)
    }

    fun isReadyToShowForm(): Boolean {
        return isRequireConsentForm() && mConsentForm != null
    }

    private fun loadForm() {
        if (mLoadingStatus != LoadConsentStatus.LOADING_FORM && isRequireConsentForm()) {
            // Loads a consent form. Must be called on the main thread.
            Log.e(TAG, "START load ConsentForm")
            mLoadingStatus = LoadConsentStatus.LOADING_FORM
            UserMessagingPlatform.loadConsentForm(appContext, { consentForm ->
                /*if (AdsConfig.getInstance().isTestGDPR) {
                    delayReturnForm(consentForm, this, callback)
                    return@loadConsentForm
                }*/
                mConsentForm = consentForm
                mLoadingStatus = LoadConsentStatus.UPDATED
                Log.e(TAG, "Load ConsentForm SUCCESS: $mConsentForm")
                mHandler.removeCallbacksAndMessages(null)
                consentFormLoaded()
            }) {
                // Handle the error.
                mLoadingStatus = LoadConsentStatus.UPDATED
                Log.e(TAG, "Load ConsentForm ERROR: ${it.message}")
                mHandler.removeCallbacksAndMessages(null)
                consentGatheringComplete(it)
            }
        }
    }

    /** Test method to delay random 3-5s, after that it will returns the consent form*/
    /*private var countDownTimer: CountDownTimer? = null
    private fun delayReturnForm(consentForm: ConsentForm, instance: GoogleConsentManager, callback: ConsentListener?) {
        *//* mConsentForm = consentForm
         isLoadingForm = false*//*

        countDownTimer?.cancel()
        val delayTime = Random().nextInt(2).toLong() + 5
        UtilsLib.showToast(appContext, "$delayTime seconds will show GDPR form")

        countDownTimer = object : CountDownTimer(delayTime * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                UtilsLib.showToast(appContext, "${millisUntilFinished / 1000} seconds will show GDPR form")
            }

            override fun onFinish() {
                mConsentForm = consentForm
                mLoadingStatus = LoadConsentStatus.UPDATED
                Log.e(TAG, "load ConsentForm success: $mConsentForm")
                mHandler.removeCallbacksAndMessages(null)
                callback?.consentFormLoaded(instance)
            }
        }
        countDownTimer?.start()
    }*/

    fun show(activity: ComponentActivity, dismissedListener: ConsentForm.OnConsentFormDismissedListener? = null): Boolean {
        if (isRequireConsentForm() && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            Log.e(TAG, "Show ConsentForm on ${activity.javaClass.simpleName}")
            if (mConsentForm == null) {
                dismissedListener?.onConsentFormDismissed(null)
                loadForm()
                return false
            }
            mConsentForm?.show(activity) {
                dismissedListener?.onConsentFormDismissed(it)
            }
            mConsentForm = null
            return true
        }
        return false
    }

    fun isRequireConsentForm(): Boolean {
        return mConsentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
    }

    /** Reset consent state */
    fun reset() {
        Log.e(TAG, "reset")
        mConsentInformation.reset()
        mConsentForm = null
        SharedPreference.setBoolean(appContext, PREF_CONSENT_ACCEPTED, false)
    }

}

interface ConsentListener {

    fun consentTimeout() {}

    fun consentFormLoaded(consentManager: GoogleConsentManager) {}

    fun consentGatheringComplete(error: FormError?)
}

private enum class LoadConsentStatus {
    NONE, REQUESTING, LOADING_FORM, UPDATED, COMPLETED
}

enum class ConsentStatus {
    NONE, REQUESTING, TIMEOUT, UPDATED, SHOWING, GATHERED
}
