package com.example.firebaseeventframework.ui.subscription

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.firebaseeventframework.event.AnalyticsEventsUtils
import com.example.firebaseeventframework.event.SubVerifyOkNoTrial1stPurchasedEv
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State của màn Subscription. Mô phỏng state machine của paywall:
 * `LoadingProducts → ShowUpgrade → Processing → ShowSuccess`.
 */
sealed interface SubscriptionUiState {
    /** Đang "tải" thông tin gói (mock delay ngắn). */
    data object LoadingProducts : SubscriptionUiState

    /** Hiển thị paywall. */
    data class ShowUpgrade(
        val price: String = "",
        val trialAvailable: Boolean = false,
        val showRestoreDialog: Boolean = false,
    ) : SubscriptionUiState

    /** Đang xử lý mua/khôi phục (chặn spam tap). */
    data object Processing : SubscriptionUiState

    /** User đã là Premium. */
    data object ShowSuccess : SubscriptionUiState
}

/**
 * ViewModel mock cho màn Subscription (demo tracking, KHÔNG dùng Google Play
 * Billing thật). Mọi "purchase"/"verify" chỉ là [delay] giả lập rồi log event.
 */
class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<SubscriptionUiState>(SubscriptionUiState.LoadingProducts)
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Mô phỏng query product details từ store
            delay(600)
            _uiState.value = SubscriptionUiState.ShowUpgrade(
                price = MOCK_PRICE,
                trialAvailable = false,
            )
        }
    }

    /** Bấm nút Subscribe → mock purchase thành công. */
    fun onPurchase() {
        val current = _uiState.value as? SubscriptionUiState.ShowUpgrade ?: return
        viewModelScope.launch {
            _uiState.value = SubscriptionUiState.Processing
            delay(1500) // mock launchBillingFlow + callback
            // iap_ev: kết quả thanh toán
            AnalyticsEventsUtils.logIap(
                where = "subscription_screen",
                paymentSuccess = true,
                isTrial = current.trialAvailable,
                productId = MOCK_PRODUCT_ID,
            )
            logFirstPurchaseIfNeeded(current.trialAvailable)
            _uiState.value = SubscriptionUiState.ShowSuccess
        }
    }

    fun onShowRestoreDialog() {
        val current = _uiState.value as? SubscriptionUiState.ShowUpgrade ?: return
        _uiState.value = current.copy(showRestoreDialog = true)
    }

    fun onDismissRestoreDialog() {
        val current = _uiState.value as? SubscriptionUiState.ShowUpgrade ?: return
        _uiState.value = current.copy(showRestoreDialog = false)
    }

    /** Xác nhận khôi phục (verify) → mock thành công. */
    fun onRestoreConfirmed() {
        val current = _uiState.value as? SubscriptionUiState.ShowUpgrade ?: return
        viewModelScope.launch {
            _uiState.value = SubscriptionUiState.Processing
            delay(1500) // mock queryPurchases + verify
            AnalyticsEventsUtils.logIap(
                where = "subscription_screen_restore",
                paymentSuccess = true,
                isTrial = false,
                productId = MOCK_PRODUCT_ID,
            )
            logFirstPurchaseIfNeeded(trialAvailable = false)
            _uiState.value = SubscriptionUiState.ShowSuccess
        }
    }

    /**
     * Log `total_sub_verifyok_notrial1stpurchased` đúng MỘT lần, cho lần mua/verify
     * thành công đầu tiên ở nhánh không trial. Cờ lưu ở [SharedPreferences].
     */
    private fun logFirstPurchaseIfNeeded(trialAvailable: Boolean) {
        if (trialAvailable) return
        val prefs = getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FIRST_PURCHASE_LOGGED, false)) return
        AnalyticsEventsUtils.logProjectEvent(SubVerifyOkNoTrial1stPurchasedEv)
        prefs.edit { putBoolean(KEY_FIRST_PURCHASE_LOGGED, true) }
    }

    private companion object {
        const val PREFS_NAME = "subscription"
        const val KEY_FIRST_PURCHASE_LOGGED = "sub_first_purchase_logged"
        const val MOCK_PRODUCT_ID = "com.example.firebaseeventframework.sub.yearly"
        const val MOCK_PRICE = "$39.99/year"
    }
}
