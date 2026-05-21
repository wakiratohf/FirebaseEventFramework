# Event Catalog

Module `:firebase-events` v1.0.0 ship sẵn 13 event chuẩn và 9 user property. Skill này sinh code wrapper xung quanh chúng. KHÔNG đụng vào module — nếu thiếu event, define custom event qua interface `AnalyticsEvent` (xem `kotlin-templates-guide.md`).

## 13 event chuẩn (`AnalyticsEvents.logXxx(...)`)

| # | Event name | Model class | Khi nào fire | Param chính |
|---|---|---|---|---|
| 1 | `screen_view_ev` | `_ScreenViewEv` | Mỗi lần vào / rời màn (gồm popup) | `screen_name`, `screen_state` (START/STOP), `popup_name`, `duration` |
| 2 | `click_btn_ev` | `_ClickBtnEv` | Mỗi lần user click nút | `screen_name`, `button_name`, `popup_name`, `time` (giây từ app open) |
| 3 | `iap_ev` | `IAPEv` | Hiển thị / hoàn tất giao dịch IAP | `where`, `payment_success`, `is_trial`, `product_id` |
| 4 | `show_rate_dialog_ev` | `_ShowRateDialogEv` | Khi rate dialog hiển thị & user chọn sao | `stars`, `where` |
| 5 | `load_ad_ev` | `LoadAdEv` | Khi request quảng cáo (thành / bại) | `ad_type`, `ad_unit_id`, `success` |
| 6 | `show_ad_ev` | `ShowAdEv` | Khi hiển thị quảng cáo | `ad_type`, `ad_unit_id`, `where` |
| 7 | `click_ad_ev` | `ClickAdEv` | Khi user click quảng cáo | `ad_type`, `ad_unit_id`, `where` |
| 8 | `onboarding_step_ev` | `OnboardingStepEv` | Khi user qua / bỏ 1 bước onboarding | `step_index`, `step_name`, `completed` |
| 9 | `notification_received_ev` | `NotificationReceivedEv` | Khi nhận push notification (foreground / background) | `campaign_id`, `title`, `payload_type` |
| 10 | `notification_clicked_ev` | `NotificationClickedEv` | Khi user mở app từ push | `campaign_id`, `title`, `deep_link` |
| 11 | `share_ev` | `ShareEv` | Khi user share content | `content_type`, `content_id`, `method` |
| 12 | `deep_link_ev` | `DeepLinkEv` | Khi app mở qua deep link | `link`, `source` |
| 13 | `app_error_ev` | `AppErrorEv` | Khi app gặp lỗi non-fatal cần track | `error_code`, `screen_name`, `message` |

Mọi event đều có header `_` (vd `_ScreenViewEv`) để báo hiệu **internal model** — không khởi tạo trực tiếp ở Activity/Fragment. Luôn đi qua `AnalyticsEventsUtils.logXxx()`.

## 9 user property (`AnalyticsUserProperties.setXxx(...)`)

| # | Property | Type | Khi nào set |
|---|---|---|---|
| 1 | `user_id` | String | Khi user đăng nhập / có FCM token |
| 2 | `screen_open` | String | Mỗi `logScreenStart` set lại với screen name hiện tại |
| 3 | `app_version` | String | `Application.onCreate` |
| 4 | `os_version` | String | `Application.onCreate` |
| 5 | `device_model` | String | `Application.onCreate` |
| 6 | `country` | String | Khi xác định được locale / IP-based country |
| 7 | `language` | String | Khi user đổi ngôn ngữ trong settings |
| 8 | `is_first_open` | Boolean | Lần đầu mở app sau install |
| 9 | `notification_enabled` | Boolean | Khi user grant / deny notification permission |

User property tuỳ chỉnh dự án (vd `preferred_protocol` cho VPN, `theme_mode`) khai báo trong `event-spec.yaml` ở section `user_properties:`. Tên ≤24 ký tự (Firebase hard limit).

## Khi nào KHÔNG dùng event chuẩn

Cần custom event khi:
- Param không khớp model chuẩn (vd `vpn_connect_ev` cần `server_country`, `protocol`, `connect_result`)
- Tên event business-specific (`document_scanned_ev`, `qr_decoded_ev`)
- Event sequence của 1 feature riêng (`onboarding_*` đã có, nhưng `tutorial_step_X_ev` thì custom)

Quy trình: thêm vào `custom_events:` trong spec → chạy `/analytics-generate` → skill sinh `YourEv.kt` data class implement `AnalyticsEvent` → call `AnalyticsEventsUtils.logProjectEvent(YourEv(...))`.

## Quy tắc đặt tên ≤40 ký tự, snake_case, kết thúc `_ev`

✅ `vpn_connect_ev`, `qr_scan_result_ev`, `document_export_ev`
❌ `VpnConnect`, `connect-event`, `vpn_connect_event_extra_long_name_that_exceeds_40_chars` (>40)
