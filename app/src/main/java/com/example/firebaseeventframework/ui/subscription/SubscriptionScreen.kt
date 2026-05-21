package com.example.firebaseeventframework.ui.subscription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebaseeventframework.R
import com.example.firebaseeventframework.event.AnalyticsEventsUtils
import com.example.firebaseeventframework.event.SubscriptionBtnEv
import com.example.firebaseeventframework.ui.theme.FirebaseEventFrameworkTheme
import java.util.Locale

// Chiều cao banner header — dùng chung để căn icon nhô ra đáy header.
private val HeaderHeight = 220.dp
private val AppIconSize = 96.dp

// Màu check xanh cho feature row (cố định, không phụ thuộc theme).
private val FeatureCheckGreen = Color(0xFF34C759)

/**
 * Màn Subscription (mock/demo). Hiển thị paywall, mô phỏng purchase/restore và
 * phát đầy đủ các event analytics. Tất cả billing là giả lập trong
 * [SubscriptionViewModel] — không tích hợp Google Play Billing thật.
 */
@Composable
fun SubscriptionScreen(
    onClose: () -> Unit,
    viewModel: SubscriptionViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Back hardware đi cùng nhánh với nút Close.
    BackHandler {
        AnalyticsEventsUtils.logClickBtn(SubscriptionBtnEv.CLOSE)
        onClose()
    }

    when (val state = uiState) {
        is SubscriptionUiState.LoadingProducts -> SubscriptionLoadingContent()
        is SubscriptionUiState.Processing -> SubscriptionUpgradeContent(
            state = SubscriptionUiState.ShowUpgrade(),
            isProcessing = true,
            onPurchase = {},
            onShowRestoreDialog = {},
            onDismissRestoreDialog = {},
            onRestoreConfirmed = {},
            onClose = {},
        )
        is SubscriptionUiState.ShowUpgrade -> SubscriptionUpgradeContent(
            state = state,
            isProcessing = false,
            onPurchase = viewModel::onPurchase,
            onShowRestoreDialog = viewModel::onShowRestoreDialog,
            onDismissRestoreDialog = viewModel::onDismissRestoreDialog,
            onRestoreConfirmed = viewModel::onRestoreConfirmed,
            onClose = {
                AnalyticsEventsUtils.logClickBtn(SubscriptionBtnEv.CLOSE)
                onClose()
            },
        )
        is SubscriptionUiState.ShowSuccess -> SubscriptionSuccessContent(
            onClose = {
                AnalyticsEventsUtils.logClickBtn(SubscriptionBtnEv.CLOSE)
                onClose()
            },
        )
    }
}

// ── Đang tải ─────────────────────────────────────────────────────────────────

@Composable
private fun SubscriptionLoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

// ── Banner header ──────────────────────────────────────────────────────────────

/** Header dạng ellipse màu primary + ngôi sao, thay cho header cam của TOH-VPN. */
@Composable
private fun SubscriptionHeader(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.fillMaxWidth().height(HeaderHeight)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = size.width / 360.dp.toPx()
            val ellipseW = 580.dp.toPx() * scaleX
            val ellipseH = 440.dp.toPx() * scaleX
            drawOval(
                color = primary,
                topLeft = Offset(-120.dp.toPx() * scaleX, -230.dp.toPx() * scaleX),
                size = Size(ellipseW, ellipseH),
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_sub_star),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .size(56.dp),
        )
    }
}

// ── Paywall ──────────────────────────────────────────────────────────────────

@Composable
private fun SubscriptionUpgradeContent(
    state: SubscriptionUiState.ShowUpgrade,
    isProcessing: Boolean,
    onPurchase: () -> Unit,
    onShowRestoreDialog: () -> Unit,
    onDismissRestoreDialog: () -> Unit,
    onRestoreConfirmed: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SubscriptionHeader()

        // Nút đóng
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 8.dp, top = 12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sub_close),
                contentDescription = stringResource(R.string.subscription_close),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }

//        // Icon app nhô ra đáy header
//        Image(
//            painter = painterResource(Row() { }.d),
//            contentDescription = null,
//            modifier = Modifier
//                .size(AppIconSize)
//                .align(Alignment.TopCenter)
//                .offset(y = HeaderHeight - AppIconSize / 2)
//                .clip(CircleShape),
//        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(HeaderHeight + AppIconSize / 2 + 16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sub_star),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.subscription_title)
                        .uppercase(Locale.getDefault()),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SubscriptionFeatureList(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 52.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            SubscriptionCtaSection(
                state = state,
                isProcessing = isProcessing,
                onPurchase = onPurchase,
                onShowRestoreDialog = onShowRestoreDialog,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (state.showRestoreDialog) {
        SubscriptionRestoreDialog(
            onDismiss = onDismissRestoreDialog,
            onConfirm = onRestoreConfirmed,
        )
    }
}

// ── Danh sách tính năng ─────────────────────────────────────────────────────

@Composable
private fun SubscriptionFeatureList(modifier: Modifier = Modifier) {
    val features = listOf(
        R.string.subscription_feature_1,
        R.string.subscription_feature_2,
        R.string.subscription_feature_3,
        R.string.subscription_feature_4,
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        features.forEach { stringRes ->
            SubscriptionFeatureRow(text = stringResource(stringRes))
        }
    }
}

@Composable
private fun SubscriptionFeatureRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(FeatureCheckGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sub_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ── CTA ──────────────────────────────────────────────────────────────────────

@Composable
private fun SubscriptionCtaSection(
    state: SubscriptionUiState.ShowUpgrade,
    isProcessing: Boolean,
    onPurchase: () -> Unit,
    onShowRestoreDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(75.dp))
                .background(if (isProcessing) primary.copy(alpha = 0.5f) else primary)
                .clickable(enabled = !isProcessing) {
                    AnalyticsEventsUtils.logClickBtn(SubscriptionBtnEv.SUBSCRIBE)
                    onPurchase()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.trialAvailable) {
                            stringResource(R.string.subscription_cta_trial)
                                .uppercase(Locale.getDefault())
                        } else {
                            stringResource(R.string.subscription_cta_subscribe)
                        },
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (state.price.isNotEmpty()) {
                        Text(
                            text = state.price,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.subscription_billing_disclaimer),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.subscription_restore),
            color = MaterialTheme.colorScheme.onSurfaceVariant
                .copy(alpha = if (isProcessing) 0.38f else 1f),
            style = MaterialTheme.typography.bodySmall,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable(enabled = !isProcessing) {
                AnalyticsEventsUtils.logClickBtn(SubscriptionBtnEv.RESTORE)
                onShowRestoreDialog()
            },
        )
    }
}

// ── Dialog khôi phục ───────────────────────────────────────────────────────────

@Composable
private fun SubscriptionRestoreDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            AnalyticsEventsUtils.logClickBtn(SubscriptionBtnEv.RESTORE_DIALOG_CANCEL)
            onDismiss()
        },
        title = { Text(stringResource(R.string.subscription_restore_dialog_title)) },
        text = { Text(stringResource(R.string.subscription_restore_dialog_message)) },
        confirmButton = {
            TextButton(onClick = {
                AnalyticsEventsUtils.logClickBtn(SubscriptionBtnEv.RESTORE_DIALOG_CONFIRM)
                onConfirm()
            }) {
                Text(stringResource(R.string.subscription_restore_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                AnalyticsEventsUtils.logClickBtn(SubscriptionBtnEv.RESTORE_DIALOG_CANCEL)
                onDismiss()
            }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

// ── Màn hình thành công ─────────────────────────────────────────────────────────

@Composable
private fun SubscriptionSuccessContent(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SubscriptionHeader()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_sub_close),
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = stringResource(R.string.subscription_success_title)
                    .uppercase(Locale.getDefault()),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        // Icon thành công nhô qua đáy header
        Box(
            modifier = Modifier
                .size(96.dp)
                .align(Alignment.TopCenter)
                .offset(y = HeaderHeight - 48.dp)
                .clip(CircleShape)
                .background(FeatureCheckGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sub_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(52.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(HeaderHeight + 48.dp + 26.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(58.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sub_star),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.subscription_success_badge)
                        .uppercase(Locale.getDefault()),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            SubscriptionFeatureList(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 52.dp),
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

// ── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Subscription — Upgrade")
@Composable
private fun SubscriptionUpgradePreview() {
    FirebaseEventFrameworkTheme {
        SubscriptionUpgradeContent(
            state = SubscriptionUiState.ShowUpgrade(price = "$39.99/year"),
            isProcessing = false,
            onPurchase = {},
            onShowRestoreDialog = {},
            onDismissRestoreDialog = {},
            onRestoreConfirmed = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true, name = "Subscription — Success")
@Composable
private fun SubscriptionSuccessPreview() {
    FirebaseEventFrameworkTheme {
        SubscriptionSuccessContent(onClose = {})
    }
}
