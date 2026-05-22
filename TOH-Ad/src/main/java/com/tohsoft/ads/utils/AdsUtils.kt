package com.tohsoft.ads.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import com.tohsoft.ads.AdsConfig
import com.tohsoft.ads.AdsConstants
import com.tohsoft.ads.AdsModule
import com.tohsoft.ads.models.AdsId
import com.tohsoft.ads.models.AdsType
import com.tohsoft.ads.utils.AdDebugLog.logd
import com.tohsoft.ads.utils.AdDebugLog.loge
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale

/**
 * Created by PhongNX on 6/8/2020.
 */
object AdsUtils {
    fun addAdsToContainer(container: ViewGroup?, adView: View?) {
        try {
            if (container == null || !AdsConfig.getInstance().canShowAd()) {
                return
            }
            if (adView != null) {
                if (adView.parent != null) {
                    if (adView.parent == container) {
                        return
                    }
                    (adView.parent as ViewGroup).removeAllViews()
                }

                container.removeAllViews()
                container.addView(adView)
                if (container.background == null) {
                    container.setBackgroundColor(Color.parseColor("#5C000000"))
                }
                setupAdContainerAttachStateListener(container)

                // Adview cách view liền kế tối thiểu 2px
                val layoutParams = adView.layoutParams
                val topMargin = 2
                if (layoutParams is LinearLayout.LayoutParams) {
                    layoutParams.topMargin = topMargin
                    layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                } else if (layoutParams is FrameLayout.LayoutParams) {
                    layoutParams.topMargin = topMargin
                    layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                } else if (layoutParams is RelativeLayout.LayoutParams) {
                    layoutParams.topMargin = topMargin
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                    layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
                }
                adView.layoutParams = layoutParams
            } else {
                setHeightForContainer(container, 0)
            }
        } catch (e: Exception) {
            loge(e)
        }
    }

    fun marginAd(adView: View?) {
        if (adView != null && adView.parent != null) {
            val layoutParams = adView.layoutParams
            val margin: Int = dp2px(12f)
            if (layoutParams is LinearLayout.LayoutParams) {
                layoutParams.setMargins(margin, margin, margin, margin)
                layoutParams.gravity = Gravity.CENTER
            } else if (layoutParams is FrameLayout.LayoutParams) {
                layoutParams.setMargins(margin, margin, margin, margin)
                layoutParams.gravity = Gravity.CENTER
            } else if (layoutParams is RelativeLayout.LayoutParams) {
                layoutParams.setMargins(margin, margin, margin, margin)
                layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
            }
            adView.layoutParams = layoutParams
        }
    }

    @Suppress("SameParameterValue")
    private fun dp2px(dpValue: Float): Int {
        val scale = Resources.getSystem().displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    private val listener: View.OnAttachStateChangeListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {}
        override fun onViewDetachedFromWindow(view: View) {
            if (view is ViewGroup) {
                view.removeAllViews()
            }
            view.removeOnAttachStateChangeListener(this)
        }
    }

    fun setupAdContainerAttachStateListener(container: View?) {
        if (container == null) {
            return
        }
        container.removeOnAttachStateChangeListener(listener)
        container.addOnAttachStateChangeListener(listener)
    }

    fun setHeightForContainer(container: View?, height: Int) {
        try {
            if (container != null) {
                val layoutParams = container.layoutParams
                if (height == 0) {
                    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                } else {
                    layoutParams.height = height + 2
                }
                container.layoutParams = layoutParams
            }
        } catch (e: Exception) {
            loge(e)
        }
    }
    
    /**
    * 
    * */
    fun isInternetAvailable(): Boolean {
        AdsModule.getInstance().context?.let { context ->
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        return false
    }

    fun getScreenWidth(): Int {
        try {
            AdsModule.getInstance().context?.let { context ->
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val display = windowManager.defaultDisplay
                display?.let {
                    val outMetrics = DisplayMetrics().apply { display.getMetrics(this) }
                    val widthPixels = outMetrics.widthPixels.toFloat()
                    val density = outMetrics.density
                    return (widthPixels / density).toInt()
                }
            }
        } catch (e: Exception) {
        }
        return 0
    }

    fun getDeviceId(context: Context): String {
        try {
            @SuppressLint("HardwareIds") val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val digest = MessageDigest.getInstance("MD5")
            digest.update(androidId.toByteArray())
            val messageDigest = digest.digest()
            // Create Hex String
            val hexString = java.lang.StringBuilder()
            for (i in messageDigest.indices) {
                val aMessageDigest = messageDigest[i]
                val h = java.lang.StringBuilder(Integer.toHexString(0xFF and aMessageDigest.toInt()))
                while (h.length < 2) h.insert(0, "0")
                hexString.append(h)
            }
            return hexString.toString().uppercase(Locale.getDefault())
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
        return ""
    }

    /**
     *
     * */
    fun readIdsFromAssetsFile(context: Context?, assetsFileName: String?): AdsId? {
        if (context == null || assetsFileName.isNullOrBlank()) {
            return null
        }

        val data = readTextFileInAsset(context, assetsFileName)
        if (data.isBlank()) {
            return null
        }

        return parseAdsIdFromJson(data)
    }

    private fun parseAdsIdFromJson(jsonData: String): AdsId? {
        return try {
            val jsonObject = JSONObject(jsonData)
            AdsId().apply {
                banner_bottom = parseStringList(jsonObject, "banner_bottom")
                banner_exit_dialog = parseStringList(jsonObject, "banner_exit_dialog")
                banner_empty_screen = parseStringList(jsonObject, "banner_empty_screen")
                interstitial = parseStringList(jsonObject, "interstitial")
                interstitial_opa = parseStringList(jsonObject, "interstitial_opa")
                app_open_ad = parseStringList(jsonObject, "app_open_ad")
            }
        } catch (e: Exception) {
            loge(e)
            null
        }
    }

    private fun parseStringList(jsonObject: JSONObject, key: String): List<String>? {
        if (!jsonObject.has(key) || jsonObject.isNull(key)) {
            return null
        }

        val jsonArray = jsonObject.optJSONArray(key) ?: return null
        val result = ArrayList<String>(jsonArray.length())

        for (i in 0 until jsonArray.length()) {
            val value = jsonArray.optString(i, "").trim()
            if (value.isNotEmpty()) {
                result.add(value)
            }
        }

        return result
    }

    fun readTextFileInAsset(context: Context, placeFileName: String): String {
        var reader: BufferedReader? = null
        try {
            reader = BufferedReader(InputStreamReader(context.assets.open(placeFileName), "UTF-8"))
            val returnString = java.lang.StringBuilder()
            var mLine: String?
            while ((reader.readLine().also { mLine = it }) != null) {
                returnString.append(mLine)
            }
            return returnString.toString().trim { it <= ' ' }
        } catch (e: IOException) {
            AdDebugLog.loge(e)
        } finally {
            if (reader != null) {
                try {
                    reader.close()
                } catch (e: IOException) {
                    AdDebugLog.loge(e)
                }
            }
        }
        return ""
    }

    // Sắp xếp Ads id theo config truyền vào
    fun mixAdsIdWithConfig(admobIds: AdsId?, fanIds: AdsId?, adsIdConfigList: List<String>): AdsId? {
        if (adsIdConfigList.isNotEmpty()) {
            val adsId = AdsId()
            // banner_bottom
            if (!admobIds?.banner_bottom.isNullOrEmpty() || !fanIds?.banner_bottom.isNullOrEmpty()) {
                logd("Mix banner_bottom")
                adsId.banner_bottom = mixAdsId(
                    admobIds?.banner_bottom,
                    fanIds?.banner_bottom,
                    adsIdConfigList
                )
            }
            // banner_exit_dialog
            if (!admobIds?.banner_exit_dialog.isNullOrEmpty() || !fanIds?.banner_exit_dialog.isNullOrEmpty()) {
                logd("Mix banner_exit_dialog")
                adsId.banner_exit_dialog = mixAdsId(
                    admobIds?.banner_exit_dialog,
                    fanIds?.banner_exit_dialog,
                    adsIdConfigList
                )
            }
            // banner_empty_screen
            if (!admobIds?.banner_empty_screen.isNullOrEmpty() || !fanIds?.banner_empty_screen.isNullOrEmpty()) {
                logd("Mix banner_empty_screen")
                adsId.banner_empty_screen = mixAdsId(
                    admobIds?.banner_empty_screen,
                    fanIds?.banner_empty_screen,
                    adsIdConfigList
                )
            }
            // interstitial
            if (!admobIds?.interstitial.isNullOrEmpty() || !fanIds?.interstitial.isNullOrEmpty()) {
                logd("Mix interstitial")
                adsId.interstitial = mixAdsId(
                    admobIds?.interstitial,
                    fanIds?.interstitial,
                    adsIdConfigList
                )
            }
            // interstitial_opa
            if (!admobIds?.interstitial_opa.isNullOrEmpty() || !fanIds?.interstitial_opa.isNullOrEmpty()) {
                logd("Mix interstitial_opa")
                adsId.interstitial_opa = mixAdsId(
                    admobIds?.interstitial_opa,
                    fanIds?.interstitial_opa,
                    adsIdConfigList
                )
            }
            // interstitial_gift
            if (!admobIds?.app_open_ad.isNullOrEmpty() || !fanIds?.app_open_ad.isNullOrEmpty()) {
                logd("Mix app_open_ad")
                adsId.app_open_ad = mixAdsId(
                    admobIds?.app_open_ad,
                    fanIds?.app_open_ad,
                    adsIdConfigList
                )
            }
            return adsId
        }
        return null
    }

    fun mixAdsId(admobIds: List<String>?, fanIds: List<String>?, adsIdConfigList: List<String>): List<String> {
        if (adsIdConfigList.isNotEmpty()) {
            val adsIdList: MutableList<String> = ArrayList()
            for (adsConfig in adsIdConfigList) { // adsConfig = ADMOB-0 | FAN-0
                var position = 0
                try {
                    // Lấy ra vị trí id cần lấy trong mảng
                    position = adsConfig.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].trim { it <= ' ' }.toInt()
                } catch (e: java.lang.Exception) {
                    loge(e)
                }

                // Kiểm tra xem position của id có trong mảng tương ứng không, nếu có thì thêm tiền tố tương ứng rồi add vào list
                if (adsConfig.lowercase(Locale.getDefault()).contains(AdsConstants.ADMOB) && admobIds != null && position < admobIds.size) {
                    adsIdList.add(admobIds[position])
                } else if (adsConfig.lowercase(Locale.getDefault()).contains(AdsConstants.FAN) && fanIds != null && position < fanIds.size) {
                    adsIdList.add(AdsConstants.FAN_ID_PREFIX + fanIds[position])
                }
            }

            logAdsId(adsIdList.toTypedArray<String>())
            return adsIdList
        }
        return ArrayList()
    }

    fun logAdsId(adsIdList: Array<String>) {
        val builder = StringBuilder()
        for (id in adsIdList) {
            builder.append("\n").append(id)
        }
        logd("Ads id:$builder")
    }

    fun mixCustomAdsIdConfig(adsId: AdsId?, admobIds: AdsId?, fanIds: AdsId?, adsType: AdsType?, adsIdConfigList: List<String>) {
        if (adsId != null && adsType != null && adsIdConfigList.isNotEmpty()) {
            logd("mixCustomAdsIdConfig - " + adsType._value)
            if (adsType == AdsType.BANNER_BOTTOM) {
                adsId.banner_bottom = mixAdsId(
                    admobIds?.banner_bottom,
                    fanIds?.banner_bottom,
                    adsIdConfigList
                )
            } else if (adsType == AdsType.BANNER_EXIT_DIALOG) {
                adsId.banner_exit_dialog = mixAdsId(
                    admobIds?.banner_exit_dialog,
                    fanIds?.banner_exit_dialog,
                    adsIdConfigList
                )
            } else if (adsType == AdsType.BANNER_EMPTY_SCREEN) {
                adsId.banner_empty_screen = mixAdsId(
                    admobIds?.banner_empty_screen,
                    fanIds?.banner_empty_screen,
                    adsIdConfigList
                )
            } else if (adsType == AdsType.INTERSTITIAL_OPA) {
                adsId.interstitial_opa = mixAdsId(
                    admobIds?.interstitial_opa,
                    fanIds?.interstitial_opa,
                    adsIdConfigList
                )
            } else if (adsType == AdsType.INTERSTITIAL) {
                adsId.interstitial = mixAdsId(
                    admobIds?.interstitial,
                    fanIds?.interstitial,
                    adsIdConfigList
                )
            } else if (adsType == AdsType.APP_OPEN_AD) {
                adsId.app_open_ad = mixAdsId(
                    admobIds?.app_open_ad,
                    fanIds?.app_open_ad,
                    adsIdConfigList
                )
            }
        }
    }

}
