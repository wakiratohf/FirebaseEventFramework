package com.example.firebaseeventframework.data

enum class TaskStatus { OPEN, DONE }

data class Task(
    val id: Long = 0L,
    val title: String,
    val status: TaskStatus = TaskStatus.OPEN,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
