package com.example.firebaseeventframework.data

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PomodoroDao(private val prefs: SharedPreferences) {

    private val gson = Gson()
    private val listType = object : TypeToken<List<PomodoroSession>>() {}.type

    private val _sessions = MutableStateFlow(loadFromPrefs())

    fun observeAll(): StateFlow<List<PomodoroSession>> = _sessions.asStateFlow()

    suspend fun insert(session: PomodoroSession): Long {
        val withId = if (session.id == 0L) session.copy(id = System.currentTimeMillis()) else session
        val next = (_sessions.value + withId).sortedByDescending { it.startedAt }
        _sessions.value = next
        prefs.edit().putString(KEY, gson.toJson(next)).apply()
        return withId.id
    }

    private fun loadFromPrefs(): List<PomodoroSession> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<PomodoroSession>>(raw, listType) ?: emptyList() }
            .getOrDefault(emptyList())
            .sortedByDescending { it.startedAt }
    }

    companion object {
        private const val KEY = "pomodoro_sessions_json"
    }
}
