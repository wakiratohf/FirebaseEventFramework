package com.tohsoft.ads.analytics

import android.os.Bundle
import androidx.core.os.bundleOf
import com.google.android.gms.ads.AdValue
import com.tohsoft.firebase_events.models.AdRevenueLike

/**
 * Adapter bọc [AdValue] của AdMob (impression-level ad revenue) vào interface
 * trung lập [AdRevenueLike] mà `:firebase-events` định nghĩa. Nhờ vậy SDK
 * analytics không phụ thuộc class doanh thu cụ thể của AdMob.
 *
 * Bundle trả về được forward nguyên trạng làm tham số của `paid_ad_impression`.
 * Key phải ≤ 40 ký tự (giới hạn tên tham số Firebase).
 *
 * Xem: https://developers.google.com/admob/android/impression-level-ad-revenue
 *
 * @param adValue payload doanh thu từ callback `onPaidEvent`.
 * @param adUnitId ad unit id phát sinh impression (tùy chọn).
 * @param adSource adapter mediation phục vụ impression (tùy chọn).
 */
class TohAdRevenue(
    private val adValue: AdValue,
    private val adUnitId: String? = null,
    private val adSource: String? = null,
) : AdRevenueLike {

    override fun toBundle(): Bundle = bundleOf(
        // Quy đổi micros → đơn vị tiền tệ chuẩn (1 đơn vị = 1_000_000 micros).
        "value" to adValue.valueMicros / 1_000_000.0,
        "currency" to adValue.currencyCode,
        "precision" to precisionName(adValue.precisionType),
        "ad_platform" to "admob",
    ).apply {
        if (!adUnitId.isNullOrEmpty()) putString("ad_unit_id", adUnitId)
        if (!adSource.isNullOrEmpty()) putString("ad_source", adSource)
    }

    private fun precisionName(precisionType: Int): String = when (precisionType) {
        AdValue.PrecisionType.ESTIMATED -> "estimated"
        AdValue.PrecisionType.PUBLISHER_PROVIDED -> "publisher_provided"
        AdValue.PrecisionType.PRECISE -> "precise"
        else -> "unknown"
    }
}
