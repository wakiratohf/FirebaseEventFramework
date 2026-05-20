package com.tohsoft.ads.models

/**
 * Kết quả hiển thị ad — giá trị cho [com.tohsoft.app_event.AdsEventTracker.logShowAd].
 *
 * Phải trùng giá trị chuỗi với `com.tohsoft.firebase_events.models.AdResult`
 * (model `ShowAdEv` map "success" → "1", còn lại → "0"). `:firebase-events`
 * mirror các hằng số này để SDK không phụ thuộc module ads.
 */
object AdResult {
    const val SUCCESS = "success"
    const val FAILED = "failed"
}
