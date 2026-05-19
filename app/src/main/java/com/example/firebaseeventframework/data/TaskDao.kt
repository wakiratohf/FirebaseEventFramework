package com.example.firebaseeventframework.data

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskDao(private val prefs: SharedPreferences) {

    private val gson = Gson()
    private val listType = object : TypeToken<List<Task>>() {}.type

    private val _tasks = MutableStateFlow(loadFromPrefs())

    fun observeAll(): StateFlow<List<Task>> = _tasks.asStateFlow()

    suspend fun getById(id: Long): Task? = _tasks.value.firstOrNull { it.id == id }

    suspend fun insert(task: Task): Long {
        val withId = if (task.id == 0L) task.copy(id = System.currentTimeMillis()) else task
        mutate { current -> current.filterNot { it.id == withId.id } + withId }
        return withId.id
    }

    suspend fun update(task: Task) {
        mutate { current -> current.map { if (it.id == task.id) task else it } }
    }

    suspend fun delete(task: Task) {
        mutate { current -> current.filterNot { it.id == task.id } }
    }

    private fun mutate(transform: (List<Task>) -> List<Task>) {
        val next = transform(_tasks.value).sortedByDescending { it.createdAt }
        _tasks.value = next
        prefs.edit().putString(KEY, gson.toJson(next)).apply()
    }

    private fun loadFromPrefs(): List<Task> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<Task>>(raw, listType) ?: emptyList() }
            .getOrDefault(emptyList())
            .sortedByDescending { it.createdAt }
    }

    companion object {
        private const val KEY = "tasks_json"
    }
}
