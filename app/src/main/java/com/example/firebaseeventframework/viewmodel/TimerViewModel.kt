package com.example.firebaseeventframework.viewmodel

import android.app.Application
import android.os.CountDownTimer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.firebaseeventframework.data.DatabaseProvider
import com.example.firebaseeventframework.data.PomodoroSession
import com.example.firebaseeventframework.data.SessionType
import com.example.firebaseeventframework.data.Task
import com.example.firebaseeventframework.data.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TimerUiState(
    val sessionType: SessionType = SessionType.WORK,
    val plannedDurationSec: Int = SessionType.WORK.defaultDurationSec(),
    val remainingSec: Int = SessionType.WORK.defaultDurationSec(),
    val running: Boolean = false,
    val linkedTask: Task? = null
)

fun SessionType.defaultDurationSec(): Int = when (this) {
    SessionType.WORK -> 25 * 60
    SessionType.SHORT_BREAK -> 5 * 60
    SessionType.LONG_BREAK -> 15 * 60
}

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    private val taskDao = DatabaseProvider.db.taskDao()
    private val pomodoroDao = DatabaseProvider.db.pomodoroDao()

    private val _state = MutableStateFlow(TimerUiState())
    val state: StateFlow<TimerUiState> = _state.asStateFlow()

    private var countDown: CountDownTimer? = null
    private var startedAt: Long = 0L

    fun bindTask(taskId: Long?) {
        if (taskId == null || taskId <= 0L) return
        viewModelScope.launch {
            val task = taskDao.getById(taskId) ?: return@launch
            _state.value = _state.value.copy(linkedTask = task)
        }
    }

    fun changeSessionType(type: SessionType) {
        if (_state.value.running) return
        val planned = type.defaultDurationSec()
        _state.value = _state.value.copy(
            sessionType = type,
            plannedDurationSec = planned,
            remainingSec = planned
        )
    }

    fun start() {
        if (_state.value.running) return
        startedAt = System.currentTimeMillis()
        startCountdown(_state.value.remainingSec)
        _state.value = _state.value.copy(running = true)
    }

    fun pause() {
        if (!_state.value.running) return
        countDown?.cancel()
        countDown = null
        _state.value = _state.value.copy(running = false)
    }

    fun reset() {
        val planned = _state.value.plannedDurationSec
        val elapsed = planned - _state.value.remainingSec
        val wasRunning = _state.value.running
        countDown?.cancel()
        countDown = null
        _state.value = _state.value.copy(
            running = false,
            remainingSec = planned
        )
        if ((wasRunning || elapsed > 0) && startedAt > 0L) {
            persistSession(
                completed = false,
                actualSec = elapsed.coerceAtLeast(0)
            )
        }
        startedAt = 0L
    }

    fun skip() {
        val planned = _state.value.plannedDurationSec
        val elapsed = planned - _state.value.remainingSec
        countDown?.cancel()
        countDown = null
        if (startedAt > 0L) {
            persistSession(completed = false, actualSec = elapsed.coerceAtLeast(0))
        }
        startedAt = 0L
        _state.value = _state.value.copy(
            running = false,
            remainingSec = planned
        )
    }

    private fun startCountdown(remainingSec: Int) {
        countDown?.cancel()
        countDown = object : CountDownTimer(remainingSec * 1000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                _state.value = _state.value.copy(
                    remainingSec = (millisUntilFinished / 1000L).toInt()
                )
            }

            override fun onFinish() {
                _state.value = _state.value.copy(remainingSec = 0, running = false)
                val planned = _state.value.plannedDurationSec
                persistSession(completed = true, actualSec = planned)
                startedAt = 0L
                _state.value = _state.value.copy(remainingSec = planned)
            }
        }.start()
    }

    private fun persistSession(completed: Boolean, actualSec: Int) {
        val type = _state.value.sessionType
        val planned = _state.value.plannedDurationSec
        val linkedId = _state.value.linkedTask?.id
        val start = startedAt
        val end = System.currentTimeMillis()
        viewModelScope.launch {
            pomodoroDao.insert(
                PomodoroSession(
                    taskId = linkedId,
                    type = type,
                    plannedDurationSec = planned,
                    actualDurationSec = actualSec,
                    completed = completed,
                    startedAt = start,
                    endedAt = end
                )
            )
        }
    }

    fun completeLinkedTask() {
        val task = _state.value.linkedTask ?: return
        if (task.status == TaskStatus.DONE) return
        viewModelScope.launch {
            val updated = task.copy(status = TaskStatus.DONE, completedAt = System.currentTimeMillis())
            taskDao.update(updated)
            _state.value = _state.value.copy(linkedTask = updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        countDown?.cancel()
        countDown = null
    }
}
