package com.example.aitasklist.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import android.provider.Settings
import com.example.aitasklist.MainActivity

class HourlySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "LAUNCH_OVERLAY") {
             val canDrawOverlays = Settings.canDrawOverlays(context)
             
             if (canDrawOverlays) {
                 val serviceIntent = Intent(context, HourlySummaryOverlayService::class.java)
                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                     context.startForegroundService(serviceIntent)
                 } else {
                     context.startService(serviceIntent)
                 }
                 Toast.makeText(context, "Launching Hourly Briefing...", Toast.LENGTH_SHORT).show()
             } else {
                 // Redirect to permission via Activity
                 val activityIntent = Intent(context, MainActivity::class.java).apply {
                     action = "REQUEST_OVERLAY_PERMISSION"
                     flags = Intent.FLAG_ACTIVITY_NEW_TASK
                     putExtra("SHOW_OVERLAY_RATIONALE", true)
                 }
                 context.startActivity(activityIntent)
                 Toast.makeText(context, "Permission Required", Toast.LENGTH_SHORT).show()
             }
        }
    }
}
