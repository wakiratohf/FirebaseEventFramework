package com.example.firebaseeventframework.data

import android.app.Application

object DatabaseProvider {

    @Volatile
    private var instance: AppDatabase? = null

    fun init(app: Application) {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    instance = AppDatabase(app.applicationContext)
                }
            }
        }
    }

    val db: AppDatabase
        get() = instance ?: error("DatabaseProvider.init(app) must be called in Application.onCreate")
}
