package com.tohsoft.ads.models

/**
 * Loại ad — giá trị cho tham số `*_type` của các sự kiện ad
 * (`load_ad_ev`, `show_ad_ev`, `click_ad_ev`). Khớp danh sách trong các model
 * `LoadAdEv` / `ShowAdEv` / `ClickAdEv` của `:firebase-events`.
 */
object AdType {
    const val BANNER = "banner"
    const val INTER = "inster"
    const val APP_OPEN = "app_open"
    const val REWARD = "reward"
    const val NATIVE = "native"
}
