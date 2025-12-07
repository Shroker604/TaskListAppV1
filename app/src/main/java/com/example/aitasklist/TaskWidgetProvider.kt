package com.example.aitasklist

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class TaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val intent = Intent(context, TaskWidgetService::class.java)
            val views = RemoteViews(context.packageName, R.layout.widget_task_list)
            
            // Set up the collection
            views.setRemoteAdapter(R.id.widget_list_view, intent)
            
            // Set the empty view
            views.setEmptyView(R.id.widget_list_view, R.id.empty_view)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
