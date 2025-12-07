package com.example.aitasklist

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.room.Room
import com.example.aitasklist.model.Task
import com.example.aitasklist.data.local.AppDatabase
import com.example.aitasklist.data.local.TaskDao

class TaskRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: MutableList<Task> = mutableListOf()

    override fun onCreate() {
        // No DB init needed
    }

    override fun onDataSetChanged() {
        // Read JSON from SharedPrefs
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("tasks_json", "[]") ?: "[]"
        
        tasks.clear()
        try {
            val jsonArray = org.json.JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                // Map PWA "text"/"completed" to Native "content"/"isCompleted"
                // Defaulting ID to random string if missing, though PWA provides it
                val t = Task(
                    id = obj.optString("id", i.toString()),
                    content = obj.optString("text", "No Title"),
                    isCompleted = obj.optBoolean("completed", false)
                )
                tasks.add(t)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tasks.add(Task(content = "Error loading tasks", isCompleted = false))
        }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position == -1 || position >= tasks.size) {
            return RemoteViews(context.packageName, R.layout.widget_task_item)
        }

        val task = tasks[position]
        val views = RemoteViews(context.packageName, R.layout.widget_task_item)
        views.setTextViewText(R.id.widget_item_text, task.content)

        val checkboxRes = if (task.isCompleted) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background
        views.setImageViewResource(R.id.widget_item_checkbox, checkboxRes)

        // Fill-in Intent for list item clicks if needed (can implement Open App)
        val fillInIntent = Intent()
        views.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}
