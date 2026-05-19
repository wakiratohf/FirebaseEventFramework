package com.example.firebaseeventframework.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.firebaseeventframework.data.DatabaseProvider
import com.example.firebaseeventframework.data.SessionType
import com.example.firebaseeventframework.data.TaskStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class StatsUiState(
    val totalTasksDone: Int = 0,
    val totalWorkMinutesToday: Int = 0,
    val totalWorkMinutesWeek: Int = 0,
    val countByType: Map<SessionType, Int> = emptyMap()
)

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val taskDao = DatabaseProvider.db.taskDao()
    private val pomodoroDao = DatabaseProvider.db.pomodoroDao()

    val uiState: StateFlow<StatsUiState> = combine(
        taskDao.observeAll(),
        pomodoroDao.observeAll()
    ) { tasks, allSessions ->
        val weekAgo = weekAgoMillis()
        val sessions = allSessions.filter { it.startedAt >= weekAgo }
        val startOfToday = startOfDayMillis()
        val todayWorkSec = sessions
            .filter { it.type == SessionType.WORK && it.startedAt >= startOfToday }
            .sumOf { it.actualDurationSec }
        val weekWorkSec = sessions
            .filter { it.type == SessionType.WORK }
            .sumOf { it.actualDurationSec }
        StatsUiState(
            totalTasksDone = tasks.count { it.status == TaskStatus.DONE },
            totalWorkMinutesToday = todayWorkSec / 60,
            totalWorkMinutesWeek = weekWorkSec / 60,
            countByType = sessions.groupingBy { it.type }.eachCount()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun startOfDayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun weekAgoMillis(): Long = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
}
