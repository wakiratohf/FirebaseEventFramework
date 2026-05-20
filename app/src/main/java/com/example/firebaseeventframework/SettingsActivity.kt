package com.example.firebaseeventframework

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.firebaseeventframework.event.AnalyticsEventsUtils
import com.example.firebaseeventframework.event.PopupName
import com.example.firebaseeventframework.event.RateDialogBtnEv
import com.example.firebaseeventframework.event.ScreenName
import com.example.firebaseeventframework.event.SettingsBtnEv
import com.example.firebaseeventframework.ui.base.BaseTrackedActivity
import com.example.firebaseeventframework.ui.dialogs.RateDialog
import com.example.firebaseeventframework.ui.dialogs.RatePrefs
import com.example.firebaseeventframework.ui.dialogs.openPlayStore
import com.example.firebaseeventframework.ui.theme.FirebaseEventFrameworkTheme

class SettingsActivity : BaseTrackedActivity() {

    override fun screenName(): String = ScreenName.SETTINGS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FirebaseEventFrameworkTheme {
                SettingsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    var showRateDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Bấm mục "Đánh giá ứng dụng" → mở rate dialog (giống mục mở dialog
            // trong TaskList): nút này log popup=NONE, các nút trong dialog log
            // popup=RATE_DIALOG bên dưới.
            SettingItem(
                title = "Đánh giá ứng dụng",
                subtitle = "Đánh giá 5 sao trên Google Play",
                onClick = {
                    AnalyticsEventsUtils.logClickBtn(SettingsBtnEv.RATE_APP)
                    showRateDialog = true
                }
            )
        }
    }

    if (showRateDialog) {
        // Log screen_view của popup (start khi mở, stop kèm duration khi đóng).
        DisposableEffect(Unit) {
            val openedAt = System.currentTimeMillis()
            AnalyticsEventsUtils.logScreenStart(ScreenName.SETTINGS, PopupName.RATE_DIALOG)
            onDispose {
                val durationSec = ((System.currentTimeMillis() - openedAt) / 1000).toInt()
                AnalyticsEventsUtils.logScreenStop(
                    ScreenName.SETTINGS, durationSec, PopupName.RATE_DIALOG
                )
            }
        }
        RateDialog(
            onRateNow = {
                AnalyticsEventsUtils.logClickBtn(RateDialogBtnEv.SETTINGS_RATE_NOW)
                // Đã rate → tắt luôn dialog back-to-exit ở Home.
                RatePrefs.setNeverShowAgain(context)
                openPlayStore(context)
                showRateDialog = false
            },
            onRateLater = {
                AnalyticsEventsUtils.logClickBtn(RateDialogBtnEv.SETTINGS_RATE_LATER)
                showRateDialog = false
            },
            onDislike = {
                AnalyticsEventsUtils.logClickBtn(RateDialogBtnEv.SETTINGS_DISLIKE)
                RatePrefs.setNeverShowAgain(context)
                showRateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingItem(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
