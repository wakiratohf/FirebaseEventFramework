package com.example.firebaseeventframework.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.firebaseeventframework.data.DatabaseProvider
import com.example.firebaseeventframework.data.Task
import com.example.firebaseeventframework.data.TaskStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskListViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = DatabaseProvider.db.taskDao()

    val tasks: StateFlow<List<Task>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTask(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            dao.insert(Task(title = trimmed))
        }
    }

    fun toggleDone(task: Task) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = if (task.status == TaskStatus.DONE) {
                task.copy(status = TaskStatus.OPEN, completedAt = null)
            } else {
                task.copy(status = TaskStatus.DONE, completedAt = now)
            }
            dao.update(updated)
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch {
            dao.delete(task)
        }
    }
}
