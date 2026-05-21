package com.example.firebaseeventframework.event

import android.os.Bundle
import com.tohsoft.firebase_events.models.AnalyticsEvent

/**
 * Event đếm tổng: log đúng MỘT lần khi user mua thành công (IAP) hoặc verify
 * (restore) thành công ở nhánh KHÔNG trial, cho lần mua đầu tiên.
 *
 * Tên event đã encode đủ ngữ nghĩa nên `toBundle()` rỗng (không cần param).
 * Stateless → dùng `object`. (Lưu ý: chỉ enum `ClickBtnEv` mới buộc dùng enum
 * cho lint; custom event `AnalyticsEvent` được phép là `object`.)
 */
object SubVerifyOkNoTrial1stPurchasedEv : AnalyticsEvent {
    override val eventName: String = "total_sub_verifyok_notrial1stpurchased"
    override fun toBundle(): Bundle = Bundle()
}
