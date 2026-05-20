package com.example.firebaseeventframework.ui.dialogs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.firebaseeventframework.event.AppOpenCounter
import com.tohsoft.app_event.RateDialogEventTracker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Popup đánh giá ứng dụng — bản Compose tái hiện `RateDialog` của app toh-weather.
 *
 * Giữ nguyên 3 hành động:
 *  - [onRateNow]   : "Đánh giá 5 sao" → mở Google Play.
 *  - [onRateLater] : "Để sau"        → reset bộ đếm.
 *  - [onDislike]   : "Không thích"   → không hiện lại nữa.
 *
 * Không cho dismiss bằng back / chạm ngoài (giống `cancelable(false)` bản gốc),
 * buộc người dùng chọn một trong ba nút.
 */
@Composable
fun RateDialog(
    onRateNow: () -> Unit,
    onRateLater: () -> Unit,
    onDislike: () -> Unit,
) {
    Dialog(
        onDismissRequest = { /* non-cancelable: bỏ qua */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "★★★★★",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFFFC107)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Đánh giá ứng dụng",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Nếu bạn thích ứng dụng, hãy dành chút thời gian " +
                        "đánh giá 5 sao cho chúng tôi nhé!",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = onRateNow,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Đánh giá 5 sao") }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = onDislike,
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) { Text("Không thích") }
                        TextButton(
                            onClick = onRateLater,
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) { Text("Để sau") }
                    }
                }
            }
        }
    }
}

/**
 * Định danh nơi rate dialog hiện lên — đi vào param `where` của
 * `show_rate_dialog_ev`. [whereHomeBack] dựng động từ ngưỡng hiện hành
 * ([RatePrefs.showOnBackPressOrdinal]) → `home_back_3rd` với cấu hình mặc định,
 * tự đổi theo nếu chỉnh ngưỡng, giữ format giống bản gốc toh-weather.
 */
fun whereHomeBack(): String = "home_back_${RatePrefs.showOnBackPressOrdinal}rd"

const val WHERE_SETTINGS = "settings"

/**
 * Tên nút gửi vào param `button_click` (theo danh mục cố định trong
 * `RATE_DIALOG_GUIDE.md` / bản gốc): `RateUs`, `NoT`, `Later`.
 */
const val RATE_BTN_RATE_US = "RateUs"
const val RATE_BTN_DISLIKE = "NoT"
const val RATE_BTN_LATER = "Later"

/**
 * Glue mỏng của `:app`: resolve counters từ store của host rồi forward vào
 * [RateDialogEventTracker] (module không tự đọc prefs). Tương đương
 * `AppUtil.logShowRateDialogEv(activity, buttonName)` bản gốc.
 *
 * Dialog không có thanh chọn sao; truyền [rateStars] = 5 cho nút "Đánh giá 5 sao"
 * (ý định 5 sao rõ ràng), mặc định 0 cho các nút còn lại.
 */
fun logRateDialogButtonEv(
    context: Context,
    where: String,
    buttonNameClicked: String,
    rateStars: Int = 0,
) {
    RateDialogEventTracker.logShowRateDialog(
        where = where,
        showCount = RatePrefs.getShowCount(context),
        appOpenedCount = AppOpenCounter.get(context),
        buttonNameClicked = buttonNameClicked,
        rateStars = rateStars,
    )
}

/**
 * Mở trang ứng dụng trên Google Play (ưu tiên app Play Store, fallback web).
 * Tương đương `Communicate.rateApp()` bản gốc.
 */
fun openPlayStore(context: Context) {
    val pkg = context.packageName
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
