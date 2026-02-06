package com.example.aitasklist

import android.app.Application
import androidx.room.Room
import com.example.aitasklist.data.local.AppDatabase

class TaskApplication : Application() {
    lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        
        // Schedule Hourly Summary
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.aitasklist.scheduler.HourlySummaryWorker>(
            1, java.util.concurrent.TimeUnit.HOURS
        ).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "HourlySummaryWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
