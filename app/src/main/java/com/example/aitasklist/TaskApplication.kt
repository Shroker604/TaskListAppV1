package com.example.aitasklist

import android.app.Application
import androidx.room.Room
import com.example.aitasklist.data.local.AppDatabase

class TaskApplication : Application() {
    lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        // Initialize ServiceLocator
        com.example.aitasklist.di.ServiceLocator.initialize(this)
        
        database = com.example.aitasklist.di.ServiceLocator.getInstance(this)
        
        // Schedule Hourly Summary
        // Helper to schedule the first work if not exists
        com.example.aitasklist.scheduler.HourlySummaryWorker.scheduleNextWork(
            this, 
            androidx.work.ExistingWorkPolicy.KEEP // Only schedule if getting started (not if already running)
        )
    }
}
