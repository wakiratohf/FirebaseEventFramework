package com.example.firebaseeventframework.data

enum class SessionType { WORK, SHORT_BREAK, LONG_BREAK }

data class PomodoroSession(
    val id: Long = 0L,
    val taskId: Long? = null,
    val type: SessionType,
    val plannedDurationSec: Int,
    val actualDurationSec: Int,
    val completed: Boolean,
    val startedAt: Long,
    val endedAt: Long
)
