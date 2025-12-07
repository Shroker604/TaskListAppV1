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
        
        val webView = android.webkit.WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }

        // Bridge to Native
        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        // Load PWA
        webView.loadUrl("file:///android_asset/index.html")
    }
}
