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
            val database = AppDatabase.getInstance(applicationContext)
            val taskDao = database.taskDao()

            val now = System.currentTimeMillis()
            val oneHourLater = now + 3600000 // 1 Hour
            val endOfDay = DateUtils.getStartOfDay(now) + (24 * 60 * 60 * 1000) - 1

            // 1. Fetch Next Hour Tasks (Critical)
            val nextHourTasks = taskDao.getTasksInRange(now, oneHourLater)

            // 2. Fetch Rest of Day Tasks
            // Overlap check: range is now -> endOfDay. Filter out those in nextHourTasks manually or adjust query?
            // Let's adjust query start time to avoid dupes?
            // Actually, querying (oneHourLater, endOfDay) is safer.
            val restOfDayTasks = taskDao.getTasksInRange(oneHourLater + 1, endOfDay)

            // Criteria for Notification:
            // - Has tasks in next hour? -> YES
            // - OR Has > 3 tasks for rest of day? -> Maybe?
            // Let's stick to user request: "Every hour to provide a summary"
            // If there are NO tasks at all, maybe skip? But user said "Every hour".
            // Let's be smart: If literally nothing is due today, maybe silent?
            // But "Unscheduled" tasks exist. 
            // Let's notify if ANY tasks exist in "Next Hour" OR "Rest of Day".
            
            if (nextHourTasks.isNotEmpty() || restOfDayTasks.isNotEmpty()) {
                sendNotification(nextHourTasks.size, restOfDayTasks.size)
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
