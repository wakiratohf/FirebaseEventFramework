package com.tohsoft.ads.models

/**
 * Hành động trong vòng đời tải ad — giá trị cho `load_ad_ev_action` của
 * `load_ad_ev`. Khớp danh sách trong model `LoadAdEv` của `:firebase-events`.
 */
object AdAction {
    /** Bắt đầu gọi load. */
    const val LOAD = "load"

    /** Thử lại sau khi load thất bại. */
    const val RETRY = "retry"

    /** Load thành công. */
    const val LOADED = "loaded"

    /** Load thất bại. */
    const val LOAD_FAILED = "load_failed"
}
