package com.example.firebaseeventframework

import android.content.Intent
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.firebaseeventframework.event.AnalyticsEventsUtils
import com.example.firebaseeventframework.event.AppOpenSource
import com.example.firebaseeventframework.event.HomeBtnEv
import com.example.firebaseeventframework.event.ScreenName
import com.example.firebaseeventframework.ui.base.BaseTrackedActivity
import com.example.firebaseeventframework.ui.theme.FirebaseEventFrameworkTheme
import com.tohsoft.app_event.OpenAppFromIntent

class MainActivity : BaseTrackedActivity() {

    override fun screenName(): String = ScreenName.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // open_app_from_ev: cold start (launcher icon → ACTION_MAIN, hoặc
        // notification/widget đã tag EXTRA_OPEN_FROM qua OpenAppFromIntent.putSource).
        OpenAppFromIntent.logFromIntent(intent, AppOpenSource.APP_ICON)
        enableEdgeToEdge()
        setContent {
            FirebaseEventFrameworkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeContent(
                        modifier = Modifier.padding(innerPadding),
                        onOpenTasks = {
                            AnalyticsEventsUtils.logClickBtn(HomeBtnEv.OPEN_TASKS)
                            startActivity(Intent(this, TaskListActivity::class.java))
                        },
                        onOpenTimer = {
                            AnalyticsEventsUtils.logClickBtn(HomeBtnEv.OPEN_TIMER)
                            startActivity(Intent(this, TimerActivity::class.java))
                        },
                        onOpenStats = {
                            AnalyticsEventsUtils.logClickBtn(HomeBtnEv.OPEN_STATS)
                            startActivity(Intent(this, StatsActivity::class.java))
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // launchMode=singleTop: notification/widget tap khi task còn sống đi vào
        // onNewIntent thay vì onCreate — phải log ở cả hai nơi.
        setIntent(intent)
        OpenAppFromIntent.logFromIntent(intent, AppOpenSource.APP_ICON)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    onOpenTasks: () -> Unit = {},
    onOpenTimer: () -> Unit = {},
    onOpenStats: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Focus Todo",
            style = MaterialTheme.typography.headlineMedium
        )
        NavCard(title = "Tasks", subtitle = "Quản lý công việc", onClick = onOpenTasks)
        NavCard(title = "Timer", subtitle = "Pomodoro 25/5", onClick = onOpenTimer)
        NavCard(title = "Stats", subtitle = "Thống kê 7 ngày", onClick = onOpenStats)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    FirebaseEventFrameworkTheme {
        HomeContent()
    }
}
