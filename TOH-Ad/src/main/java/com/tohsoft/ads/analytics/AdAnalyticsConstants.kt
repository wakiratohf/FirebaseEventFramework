package com.tohsoft.ads.analytics

/**
 * Loại ad — giá trị cho tham số `*_type` của các sự kiện ad
 * (`load_ad_ev`, `show_ad_ev`, `click_ad_ev`). Khớp danh sách trong các model
 * `LoadAdEv` / `ShowAdEv` / `ClickAdEv` của `:firebase-events`.
 *
 * (Bê từ module `:ads` cũ — giữ nguyên giá trị để tương thích event catalog.)
 */
object AdType {
    const val BANNER = "banner"
    const val INTER = "inster"
    const val APP_OPEN = "app_open"
    const val REWARD = "reward"
    const val NATIVE = "native"
}

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
