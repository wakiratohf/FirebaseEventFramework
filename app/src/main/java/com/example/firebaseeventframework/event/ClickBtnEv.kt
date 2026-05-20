package com.example.firebaseeventframework.event

/**
 * Marker interface mọi enum nút bấm phải implement.
 *
 * `:firebase-events-lint`'s `ButtonNameConventionDetector` match enum nào
 * implement interface có **simple name** đúng `ClickBtnEv` (bất kỳ package),
 * rồi kiểm tra [buttonName] (constructor arg thứ 2) theo convention:
 * camelCase, không `_`, không prefix `btn`, không rỗng.
 *
 * KHÔNG đổi tên interface và KHÔNG dùng `object` phẳng thay cho enum —
 * cả hai đều khiến Lint rule ngừng fire (xem PROJECT_EVENT_TEMPLATE.md).
 */
interface ClickBtnEv {
    val screenName: String
    val buttonName: String
    val popupName: String
}
