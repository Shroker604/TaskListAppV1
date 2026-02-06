package com.example.aitasklist.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.aitasklist.MainActivity
import com.example.aitasklist.R
import com.example.aitasklist.TaskApplication
import com.example.aitasklist.data.local.AppDatabase
import com.example.aitasklist.util.DateUtils
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HourlySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Use ServiceLocator
            val briefingManager = com.example.aitasklist.di.ServiceLocator.briefingManager
            val database = com.example.aitasklist.di.ServiceLocator.getInstance(applicationContext)
            val taskDao = database.taskDao()

            if (briefingManager == null) {
                 // Fallback if ServiceLocator not ready (unlikely)
                 return@withContext Result.failure()
            }

            val now = System.currentTimeMillis()
            
            // Use BriefingManager logic
            val briefing = briefingManager.getBriefingFromDao(now, taskDao)

            val nextHourCount = briefing.nextHourTasks.size
            val restOfDayCount = briefing.restOfDayTasks.size
            
            if (nextHourCount > 0 || restOfDayCount > 0) {
                sendNotification(nextHourCount, restOfDayCount)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun sendNotification(nextHourCount: Int, restOfDayCount: Int) {
        val channelId = "hourly_briefing_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel if not exists
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Hourly Briefing",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Hourly summary of your upcoming tasks"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent to open Main Activity with "Show Summary" flag
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("SHOW_HOURLY_SUMMARY", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Hourly Briefing"
        val content = when {
            nextHourCount > 0 -> "$nextHourCount urgent tasks in the next hour."
            else -> "You have $restOfDayCount tasks for the rest of the day."
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // System icon fallback
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
