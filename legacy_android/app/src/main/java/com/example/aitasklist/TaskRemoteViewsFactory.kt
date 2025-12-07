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

    private var tasks: List<Task> = emptyList()
    private lateinit var database: AppDatabase
    private lateinit var taskDao: TaskDao

    override fun onCreate() {
        database = AppDatabase.getInstance(context)
        taskDao = database.taskDao()
    }

    override fun onDataSetChanged() {
        // This is called when we notifyAppWidgetViewDataChanged
        // It runs on a background thread, so we can do DB operations here.
        tasks = taskDao.getAllTasksSync()
    }

    override fun onDestroy() {
        // Database is a singleton, do not close it here
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position == -1 || position >= tasks.size) {
            return RemoteViews(context.packageName, R.layout.widget_task_item)
        }

        val task = tasks[position]
        val views = RemoteViews(context.packageName, R.layout.widget_task_item)
        views.setTextViewText(R.id.widget_item_text, task.content)

        // Simple visual indication for completed tasks (optional, could add strikethrough logic here if needed)
        val checkboxRes = if (task.isCompleted) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background
        views.setImageViewResource(R.id.widget_item_checkbox, checkboxRes)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
