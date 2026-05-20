package com.example.firebaseeventframework

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebaseeventframework.data.SessionType
import com.example.firebaseeventframework.event.AnalyticsEventsUtils
import com.example.firebaseeventframework.event.ScreenName
import com.example.firebaseeventframework.event.TimerBtnEv
import com.example.firebaseeventframework.ui.base.BaseTrackedActivity
import com.example.firebaseeventframework.ui.theme.FirebaseEventFrameworkTheme
import com.example.firebaseeventframework.viewmodel.TimerViewModel

class TimerActivity : BaseTrackedActivity() {

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
    }

    override fun screenName(): String = ScreenName.TIMER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L).takeIf { it > 0L }
        setContent {
            FirebaseEventFrameworkTheme {
                TimerScreen(taskId = taskId)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerScreen(taskId: Long?, viewModel: TimerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(taskId) { viewModel.bindTask(taskId) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(text = "Pomodoro") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            state.linkedTask?.let {
                Text(text = "Task: ${it.title}", style = MaterialTheme.typography.bodyLarge)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionType.entries.forEach { type ->
                    FilterChip(
                        selected = state.sessionType == type,
                        onClick = {
                            AnalyticsEventsUtils.logClickBtn(TimerBtnEv.CHANGE_SESSION_TYPE)
                            viewModel.changeSessionType(type)
                        },
                        enabled = !state.running,
                        label = { Text(type.displayLabel()) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = formatTime(state.remainingSec),
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.running) {
                    Button(onClick = {
                        AnalyticsEventsUtils.logClickBtn(TimerBtnEv.START)
                        viewModel.start()
                    }) { Text("Start") }
                } else {
                    OutlinedButton(onClick = {
                        AnalyticsEventsUtils.logClickBtn(TimerBtnEv.PAUSE)
                        viewModel.pause()
                    }) { Text("Pause") }
                }
                OutlinedButton(onClick = {
                    AnalyticsEventsUtils.logClickBtn(TimerBtnEv.RESET)
                    viewModel.reset()
                }) { Text("Reset") }
                OutlinedButton(onClick = {
                    AnalyticsEventsUtils.logClickBtn(TimerBtnEv.SKIP)
                    viewModel.skip()
                }) { Text("Skip") }
            }

            state.linkedTask?.let {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = {
                    AnalyticsEventsUtils.logClickBtn(TimerBtnEv.MARK_TASK_DONE)
                    viewModel.completeLinkedTask()
                }) {
                    Text("Mark task done")
                }
            }
        }
    }
}

private fun SessionType.displayLabel(): String = when (this) {
    SessionType.WORK -> "Work"
    SessionType.SHORT_BREAK -> "Short break"
    SessionType.LONG_BREAK -> "Long break"
}

private fun formatTime(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}
