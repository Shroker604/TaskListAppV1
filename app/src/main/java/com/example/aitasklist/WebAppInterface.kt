package com.example.aitasklist

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.appwidget.AppWidgetManager
import android.content.ComponentName

class WebAppInterface(private val context: Context) {

    @JavascriptInterface
    fun updateWidgets(jsonTasks: String) {
        // 1. Save JSON to SharedPreferences
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("tasks_json", jsonTasks).apply()

        // 2. Trigger Widget Update
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, TaskWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        
        // Notify Provider to update
        val intent = Intent(context, TaskWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        }
        context.sendBroadcast(intent)
        
        // Notify List View to refresh data
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view)
    }
}
