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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDarkTheme = remember { mutableStateOf(false) }
            
            MaterialTheme(
                colorScheme = if (isDarkTheme.value) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TaskListScreen(
                        isDarkTheme = isDarkTheme.value,
                        onThemeToggle = { isDarkTheme.value = !isDarkTheme.value }
                    )
                }
            }
        }
    }
}
