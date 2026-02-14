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

    companion object {
        // Debug until: 2026-02-08 06:00:00
        private const val DEBUG_INTERVAL_MS = 15 * 60 * 1000L // 15 Minutes
        private const val NORMAL_INTERVAL_MS = 60 * 60 * 1000L // 1 Hour
        private const val DEBUG_EXPIRATION_TIMESTAMP = 1770638400000L // Feb 8 2026, 4:00 AM approx (rough guess based on current 2026 date) 
        // Current Time in user connection: 2026-02-07T21:06 -> Feb 7.
        // So "after tonight" = Feb 8 morning. 
        // Let's dynamically calculate today + 12 hours? No, static is safer for this snippet.
        // I'll use a dynamic check in scheduleNextWork.

        fun scheduleNextWork(context: Context, policy: androidx.work.ExistingWorkPolicy) {
            val now = System.currentTimeMillis()
            
            // Hardcoding a "Debugging End" time: Feb 8th, 2026, 08:00 AM.
            val limit = java.util.Calendar.getInstance().apply {
                set(2026, 1, 8, 8, 0) 
            }.timeInMillis
            
            val delay = if (now < limit) DEBUG_INTERVAL_MS else NORMAL_INTERVAL_MS
            
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<HourlySummaryWorker>()
                .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("HourlySummaryWork")
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "HourlySummaryWork",
                policy, 
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Use ServiceLocator
            val briefingManager = com.example.aitasklist.di.ServiceLocator.briefingManager
            val database = com.example.aitasklist.di.ServiceLocator.getInstance(applicationContext)
            val taskDao = database.taskDao()

            if (briefingManager == null) {
                 return@withContext Result.failure()
            }

            val now = System.currentTimeMillis()
            
            val briefing = briefingManager.getBriefingFromDao(now, taskDao)

            val nextHourCount = briefing.nextHourTasks.size
            val restOfDayCount = briefing.restOfDayTasks.size
            
            // DEBUG: Always send notification to prove worker ran
            // Real logic: if (nextHourCount > 0 || restOfDayCount > 0)
            val overdueCount = briefing.overdueTasks.size
            
            // DEBUG: Always send notification to prove worker ran
            // Real logic: if (nextHourCount > 0 || restOfDayCount > 0 || overdueCount > 0)
            if (overdueCount == 0 && nextHourCount == 0 && restOfDayCount == 0) {
                // Determine if we should really suppress or if this is a "No tasks expected" keep-alive.
                // Request was: "If there is no tasks for the remainder of the day and no tasks within the next hour, don't send h hourly briefing."
                // So we return success (done) without notification.
                return@withContext Result.success()
            }

            sendNotification(nextHourCount, restOfDayCount, overdueCount)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        } finally {
            // Schedule the NEXT run after we are done (or failed)
            // Use REPLACE to ensure we chain the next one
            scheduleNextWork(applicationContext, androidx.work.ExistingWorkPolicy.REPLACE)
        }
    }

    private fun sendNotification(nextHourCount: Int, restOfDayCount: Int, overdueCount: Int) {
        val channelId = "hourly_briefing_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

        val canDrawOverlays = android.provider.Settings.canDrawOverlays(applicationContext)
        
        // UNIQUE REQUEST CODE to prevent collision
        val uniqueRequestCode = (System.currentTimeMillis() % 10000).toInt()
        
        val intent = if (canDrawOverlays) {
             Intent(applicationContext, HourlySummaryReceiver::class.java).apply {
                action = "LAUNCH_OVERLAY"
             }
        } else {
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                action = "REQUEST_OVERLAY_PERMISSION"
            }
        }
        
        val pendingIntent = if (canDrawOverlays) {
            PendingIntent.getBroadcast(
                applicationContext, 
                uniqueRequestCode, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
             PendingIntent.getActivity(
                applicationContext, 
                uniqueRequestCode, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        
        // Content Logic
        val title = "Hourly Briefing"
        val content = StringBuilder()
        
        if (overdueCount > 0) {
            content.append("$overdueCount Overdue! ")
        }
        
        if (nextHourCount > 0) {
             content.append("$nextHourCount next hour. ")
        }
        
        // Always append daily if present, regardless of next hour
        if (restOfDayCount > 0) {
            content.append("$restOfDayCount remaining today.")
        } else if (overdueCount == 0 && nextHourCount == 0) {
            // Fallback for empty (should be caught by suppression, but safe to have)
            content.append("No pending tasks.")
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder) 
            .setContentTitle(title)
            .setContentText(content.toString())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
