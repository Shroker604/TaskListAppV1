package com.example.aitasklist.scheduler

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.aitasklist.MainActivity
import com.example.aitasklist.R
import com.example.aitasklist.model.Priority

class ReminderReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_COMPLETE = "com.example.aitasklist.ACTION_COMPLETE"
        const val ACTION_RESCHEDULE = "com.example.aitasklist.ACTION_RESCHEDULE"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("taskId") ?: return
        val content = intent.getStringExtra("content") ?: return
        val priorityName = intent.getStringExtra("priority") ?: "LOW"
        
        // Handle Actions
        if (intent.action == ACTION_COMPLETE) {
             // Logic to mark task complete would ideally go through ViewModel or Repository
             // For now, we mainly need to CANCEL the specific repeating alarm
             val reminderManager = ReminderManager(context)
             reminderManager.cancelReminder(taskId)
             
             // Dismiss notification
             val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
             notificationManager.cancel(taskId.hashCode())
             return
        } else if (intent.action == ACTION_RESCHEDULE) {
            val reminderManager = ReminderManager(context)
            // Reschedule for 1 hour later
            reminderManager.cancelReminder(taskId) // Cancel old one first to be safe
            reminderManager.scheduleReminder(taskId, content, System.currentTimeMillis() + 3600000, priorityName)
            
            // Dismiss notification
             val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
             notificationManager.cancel(taskId.hashCode())
             return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val tapPendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Action Intents
        val completeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_COMPLETE
            putExtra("taskId", taskId)
            putExtra("content", content)
            putExtra("priority", priorityName)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode() + 1, // Unique ID
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val rescheduleIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_RESCHEDULE
            putExtra("taskId", taskId)
            putExtra("content", content)
            putExtra("priority", priorityName)
        }
        val reschedulePendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode() + 2, // Unique ID
            rescheduleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        
        val notificationBuilder = NotificationCompat.Builder(context, ReminderManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Task Reminder: $priorityName Priority")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapPendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            
        if (priorityName == "HIGH") {
            // For high priority, make it annoying (alert once doesn't mean stop)
            // But android notifications don't inherently loop sound usually, the AlarmManager handles the re-triggering.
            notificationBuilder.setFullScreenIntent(tapPendingIntent, true) // Maybe too aggressive? Let's stick to standard high prio
            notificationBuilder.addAction(android.R.drawable.ic_menu_agenda, "Reschedule +1h", reschedulePendingIntent)
            notificationBuilder.addAction(android.R.drawable.checkbox_on_background, "Complete", completePendingIntent)
        } else {
             notificationBuilder.addAction(android.R.drawable.ic_menu_agenda, "Snooze +1h", reschedulePendingIntent)
        }
            
        // Use a unique ID for the notification, could use task ID hash code
        notificationManager.notify(taskId.hashCode(), notificationBuilder.build())
    }
}
