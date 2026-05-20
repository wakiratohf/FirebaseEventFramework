package com.example.firebaseeventframework

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebaseeventframework.data.SessionType
import com.example.firebaseeventframework.event.ScreenName
import com.example.firebaseeventframework.ui.base.BaseTrackedActivity
import com.example.firebaseeventframework.ui.theme.FirebaseEventFrameworkTheme
import com.example.firebaseeventframework.viewmodel.StatsViewModel

class StatsActivity : BaseTrackedActivity() {

    override fun screenName(): String = ScreenName.STATS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FirebaseEventFrameworkTheme {
                StatsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsScreen(viewModel: StatsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Stats") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatRow("Tasks đã hoàn thành", state.totalTasksDone.toString())
            StatRow("Phút Work hôm nay", state.totalWorkMinutesToday.toString())
            StatRow("Phút Work 7 ngày", state.totalWorkMinutesWeek.toString())
            Text("Sessions theo loại", style = MaterialTheme.typography.titleMedium)
            SessionType.entries.forEach { type ->
                StatRow(
                    label = "  ${type.name}",
                    value = (state.countByType[type] ?: 0).toString()
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
