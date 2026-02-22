package com.example.aitasklist.scheduler

import com.example.aitasklist.data.local.TaskDao
import com.example.aitasklist.data.repository.CalendarRepository
import com.example.aitasklist.model.Task
import com.example.aitasklist.scheduler.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskScheduleManager(
    private val taskDao: TaskDao,
    private val calendarRepository: CalendarRepository,
    private val reminderManager: ReminderManager
) {

    suspend fun updateTaskSchedule(task: Task, newDate: Long, newTime: Long?) {
        withContext(Dispatchers.IO) {
            // If Time is set: Date = Time (Exact). Reminder = Time.
            // If Time is NULL: Date = Midnight (All Day). Reminder = NULL.
            val finalScheduledDate = newTime ?: newDate
            val finalReminderTime = newTime
            
            // Update Task in DB
            val updatedTask = task.copy(
                scheduledDate = finalScheduledDate,
                reminderTime = finalReminderTime
            )
            taskDao.updateTask(updatedTask)
            
            // Handle Reminder Scheduling
            if (finalReminderTime != null) {
                reminderManager.scheduleReminder(updatedTask.id, updatedTask.content, finalReminderTime, updatedTask.priority.name)
            } else {
                reminderManager.cancelReminder(updatedTask.id)
            }

            // Handle Calendar Sync
            if (updatedTask.calendarEventId != null) {
                // If reminderTime is null -> All Day. Else -> Timed.
                val isAllDay = (finalReminderTime == null)
                val endTime = if (isAllDay) {
                    finalScheduledDate + (24 * 60 * 60 * 1000)
                } else {
                    finalScheduledDate + 3600000
                }
                
                calendarRepository.updateCalendarEvent(
                    eventId = updatedTask.calendarEventId,
                    title = updatedTask.content,
                    description = "Created from AI Task List",
                    startTime = finalScheduledDate,
                    endTime = endTime,
                    isAllDay = isAllDay
                )
            }
        }
    }
    
    suspend fun addToCalendar(task: Task): Pair<Long, String>? {
        return withContext(Dispatchers.IO) {
            // Default to now if no date
            val startTime = if (task.scheduledDate != 0L) task.scheduledDate else System.currentTimeMillis()
            val isAllDay = task.reminderTime == null
            val endTime = if (isAllDay) startTime + (24 * 60 * 60 * 1000) else startTime + 3600000
            
            val result = calendarRepository.addToCalendar(
                title = task.content,
                description = "Created from AI Task List",
                startTime = startTime,
                endTime = endTime,
                isAllDay = isAllDay
            )
            
            // Update task with new event ID if successful
            if (result != null) {
                 val (eventId, _) = result
                 taskDao.updateTask(task.copy(calendarEventId = eventId))
                 result
            } else {
                null
            }
        }
    }

    suspend fun updateTaskDate(task: Task, newDate: Long) {
        // If there's a reminder, we need to move it to the new date
        val newReminderTime = if (task.reminderTime != null) {
            val oldReminder = java.util.Calendar.getInstance().apply { timeInMillis = task.reminderTime }
            val newReminder = java.util.Calendar.getInstance().apply { timeInMillis = newDate }
            
            // Keep the time from old reminder
            newReminder.set(java.util.Calendar.HOUR_OF_DAY, oldReminder.get(java.util.Calendar.HOUR_OF_DAY))
            newReminder.set(java.util.Calendar.MINUTE, oldReminder.get(java.util.Calendar.MINUTE))
            newReminder.set(java.util.Calendar.SECOND, 0)
            newReminder.set(java.util.Calendar.MILLISECOND, 0)
            
            newReminder.timeInMillis
        } else {
            null
        }
        
        updateTaskSchedule(task, newDate, newReminderTime)
    }
}
