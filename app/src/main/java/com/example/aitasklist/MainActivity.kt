package com.example.aitasklist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.example.aitasklist.ui.TaskListScreen

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: com.example.aitasklist.TaskViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel = androidx.lifecycle.ViewModelProvider(this)[com.example.aitasklist.TaskViewModel::class.java]
        
        // Handle Overlay Actions - Basic check for LAUNCH_OVERLAY (service start) logic
        // But for permission rationale, we need the ViewModel.
        // We can create the ViewModel here or lazily inside setContent?
        // Let's rely on setContent to create VM, then check intent.
        
        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            
            MaterialTheme(
                colorScheme = if (isDarkTheme) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TaskListScreen(
                        viewModel = viewModel,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = { viewModel.setDarkTheme(!isDarkTheme) }
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent) 
        
        android.widget.Toast.makeText(this, "New Intent Action: ${intent.action}", android.widget.Toast.LENGTH_LONG).show()
        
        if (intent.action == "LAUNCH_OVERLAY") {
              handleOverlayIntents() 
        } else if (intent.action == "REQUEST_OVERLAY_PERMISSION") {
             if (::viewModel.isInitialized) {
                 viewModel.setShowOverlayPermissionRationale(true)
             }
        }
    }

    private fun handleOverlayIntents(): Boolean {
        if (intent?.action == "LAUNCH_OVERLAY") {
             val serviceIntent = android.content.Intent(this, com.example.aitasklist.scheduler.HourlySummaryOverlayService::class.java)
             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                 startForegroundService(serviceIntent)
             } else {
                 startService(serviceIntent)
             }
             finish()
             return true
        }
        
        if (intent?.action == "REQUEST_OVERLAY_PERMISSION") {
            // Trigger the dialog logic in UI
            if (::viewModel.isInitialized) {
                viewModel.setShowOverlayPermissionRationale(true)
            }
            return false 
        }
        
        return false
    }
}
