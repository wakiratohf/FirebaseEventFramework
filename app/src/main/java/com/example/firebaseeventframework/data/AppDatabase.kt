package com.example.firebaseeventframework.data

import android.content.Context

class AppDatabase(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "focus_todo_store", Context.MODE_PRIVATE
    )

    private val taskDaoImpl = TaskDao(prefs)
    private val pomodoroDaoImpl = PomodoroDao(prefs)

    fun taskDao(): TaskDao = taskDaoImpl
    fun pomodoroDao(): PomodoroDao = pomodoroDaoImpl
}
