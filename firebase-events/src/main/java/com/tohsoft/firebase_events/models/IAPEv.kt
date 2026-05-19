package com.tohsoft.firebase_events.models

import android.os.Bundle

/**
* Khi user thực hiện mua trong ứng dụng
 *
 * @param where: vị trí hiển thị màn hình iap:
 * - Nếu auto show khi mở app: "open_app"
 * - Nếu auto show khi hết onboarding: "onboarding"
 * - Show khi user chọn pro icon: "screenName_pro_icon"
 * - Show khi user mở tính năng bị lock: "screenName_unlock_xxx" (xxx = tên tính năng)
 *
 * @param paymentSuccess: tên các trạng thái thanh toán trả về
 * 0 - thanh toán thất bại (bao gồm cả user cancel tại purchase dialog)
 * 1 - thanh toán thành công (có bao gồm trial)
 *
 * @param isTrial: Param để xác định đây có phải là trial hay không
 *
 * @param productId: Param để xác định đây có phải là trial hay không (SKU)
* */
data class IAPEv(
    private val where: String,
    private val paymentSuccess: Boolean,
    private val isTrial: Boolean,
    private val productId: String,
) {

    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString("iap_ev_where", where)
        bundle.putString("iap_ev_result", if (paymentSuccess) "1" else "0")
        bundle.putString("iap_is_trial", if (isTrial) "1" else "0")
        bundle.putString("ipa_package_name", productId)
        return bundle
    }
}