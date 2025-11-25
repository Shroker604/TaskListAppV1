package com.example.aitasklist

import android.app.Application
import androidx.room.Room

class TaskApplication : Application() {
    lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
    }
}
