package com.example.aitasklist.scheduler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.aitasklist.R
import com.example.aitasklist.ui.HourlySummarySheet
import com.example.aitasklist.util.WindowLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.aitasklist.TaskViewModel
import com.example.aitasklist.di.ServiceLocator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.aitasklist.ui.HourlySummaryContent
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory


class HourlySummaryOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private lateinit var lifecycleOwner: WindowLifecycleOwner
    
    // Minimal Scope for Service operations
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        lifecycleOwner = WindowLifecycleOwner()
        lifecycleOwner.onCreate()
        
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) {
            showOverlay()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        android.util.Log.d("HourlyOverlay", "showOverlay called")
        if (!Settings.canDrawOverlays(this)) {
            android.util.Log.e("HourlyOverlay", "Permission Missing!")
            showPermissionRequiredNotification()
            stopSelf()
            return
        }
        
        android.util.Log.d("HourlyOverlay", "Permission Granted, creating view...")

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT, // Full screen to capture touches for dismissal
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            dimAmount = 0.5f // Dim background apps
        }

        overlayView = ComposeView(this).apply {
            // Attach Lifecycle Owners required for Compose
            this.setViewTreeLifecycleOwner(lifecycleOwner)
            this.setViewTreeViewModelStoreOwner(lifecycleOwner)
            this.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                val app = application as com.example.aitasklist.TaskApplication
                
                // Direct dependencies (Lightweight)
                val briefingManager = ServiceLocator.briefingManager
                val taskDao = ServiceLocator.provideTaskDao(applicationContext)
                val preferencesRepo = ServiceLocator.userPreferencesRepository

                // Theme State
                // Default to false (Light) if null, or collect safely
                val isDarkTheme by preferencesRepo?.isDarkTheme?.collectAsState(initial = false) ?: mutableStateOf(false)
                
                // Data State
                var briefing by remember { mutableStateOf<com.example.aitasklist.domain.HourlyBriefing?>(null) }
                
                // Fetch Data (Lightweight)
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (briefingManager != null) {
                         briefing = briefingManager.getBriefingFromDao(System.currentTimeMillis(), taskDao)
                    }
                }

                // Animation State
                var isVisible by remember { mutableStateOf(false) }
                
                // Trigger animation on start
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    isVisible = true
                }
                
                fun dismiss() {
                    isVisible = false
                    // We need to wait for animation to finish before stopping service due to composition
                    // But we can't easily block here. 
                    // Simpler: Just stopSelf() which kills the window. Animation is abrupt but fine for "Close".
                    // OR: Use a delayed effect.
                }

                MaterialTheme(
                    colorScheme = if (isDarkTheme) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
                ) {
                    // Transparent surface to allow seeing behind
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent 
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) { 
                                     stopSelf() // Click outside to dismiss
                                },
                            contentAlignment = androidx.compose.ui.Alignment.BottomCenter
                        ) {
                             androidx.compose.animation.AnimatedVisibility(
                                 visible = isVisible,
                                 enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
                                 exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
                             ) {
                                 // Stop clicks from propagating to Box (Scrim)
                                 Surface(
                                     modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = false) {}, // Consume clicks
                                     shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp), bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)),
                                     color = MaterialTheme.colorScheme.surface,
                                     tonalElevation = 6.dp
                                 ) {
                                    // Render Content if loaded
                                    if (briefing != null) {
                                        com.example.aitasklist.ui.HourlySummaryContent(
                                            overdueTasks = briefing!!.overdueTasks,
                                            nextHourTasks = briefing!!.nextHourTasks,
                                            restOfDayTasks = briefing!!.restOfDayTasks,
                                            unscheduledTasks = briefing!!.unscheduledTasks,
                                            onTaskClick = { 
                                                // TODO: Handle navigation to main app
                                                stopSelf()
                                            }
                                        )
                                    } else {
                                        // Loading State (optional, likely too fast to need one)
                                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            androidx.compose.material3.CircularProgressIndicator()
                                        }
                                    }
                                 }
                             }
                        }
                    }
                }
            }
        }

        lifecycleOwner.onResume()
        windowManager.addView(overlayView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView != null) {
            lifecycleOwner.onPause()
            lifecycleOwner.onDestroy()
            windowManager.removeView(overlayView)
            overlayView = null
        }
        serviceScope.cancel()
    }

    private fun createNotification(): Notification {
        val channelId = "overlay_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Hourly Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return android.app.Notification.Builder(this, channelId)
            .setContentTitle("Hourly Briefing Active")
            .setContentText("Tap to dismiss")
            .setSmallIcon(android.R.drawable.ic_popup_reminder) 
            .build()
    }

    private fun showPermissionRequiredNotification() {
        val channelId = "overlay_service_channel"
        // Ensure channel exists (it's created in onStartCommand/createNotification, but just in case)
        
        val intent = Intent(this, com.example.aitasklist.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            action = "REQUEST_OVERLAY_PERMISSION"
            putExtra("SHOW_OVERLAY_RATIONALE", true)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        
        val notification = android.app.Notification.Builder(this, channelId)
            .setContentTitle("Permission Needed")
            .setContentText("Tap to enable 'Display over other apps' for Hourly Summary.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
    }
}
