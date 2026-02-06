package com.example.aitasklist.domain

import com.example.aitasklist.data.repository.CalendarRepository
import com.example.aitasklist.data.local.TaskDao
import com.example.aitasklist.scheduler.AutoScheduler
import com.example.aitasklist.scheduler.CalendarGapManager
import com.example.aitasklist.scheduler.ReminderManager
import com.example.aitasklist.model.Task
import com.example.aitasklist.util.DateUtils
import com.example.aitasklist.data.repository.CalendarEvent
import com.example.aitasklist.scheduler.TimeSlot
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncManager(
    private val calendarRepository: CalendarRepository,
    private val taskDao: TaskDao,
    private val autoScheduler: AutoScheduler,
    private val calendarGapManager: CalendarGapManager,
    private val reminderManager: ReminderManager
) {

    suspend fun performFullSync(
        windowStart: Long,
        windowEnd: Long,
        excludedCalendarIds: Set<String>,
        allTasks: List<Task>,
        deadlineHour: Int = 23 // Default if not passed
    ): SyncResult = withContext(Dispatchers.IO) {
        
        // 1. Fetch Calendar Events (Busy Slots)
        var events = calendarRepository.getEventsInRange(windowStart, windowEnd, excludedCalendarIds)

        // 2. PULL SYNC
        val importedCount = performPullSync(events)

        // 3. PUSH SYNC
        val manualPushedCount = performPushSync(windowStart, windowEnd, allTasks)

        // Refetch if needed
        if (manualPushedCount > 0) {
            events = calendarRepository.getEventsInRange(windowStart, windowEnd, excludedCalendarIds)
        }

        // 4. AUTO-SCHEDULE
        // Auto-schedule needs unscheduled tasks.
        // We need to fetch fresh tasks because PULL might have added some, PUSH updated some.
        // But for efficiency, we can filter from allTasks but that might be stale.
        // Let's assume we want to schedule *currently* unscheduled tasks.
        val unscheduledTasks = taskDao.getUnscheduledTasks()
        
        // Filter out completed if DAO doesn't do it (DAO does: WHERE isDeleted = 0 AND isCompleted = 0)
        // So we can use it directly.

        val autoScheduledCount = performAutoSchedule(windowStart, windowEnd, events, unscheduledTasks)

        val busyCount = events.size
        val busySlots = events.map { TimeSlot(it.startTime, it.endTime) }
        val gaps = calendarGapManager.findGaps(windowStart, windowEnd, busySlots)

        SyncResult(
             eventsCount = busyCount,
             gapsCount = gaps.size,
             scheduledCount = autoScheduledCount,
             importedCount = importedCount,
             pushedCount = manualPushedCount
        )
    }

    private suspend fun performPullSync(events: List<CalendarEvent>): Int {
        var count = 0
        events.forEach { event ->
            val existingTask = findMatchingTask(event)
            
            // If Soft Deleted, SKIP (Prevent Resync)
            if (existingTask != null && existingTask.isDeleted) {
                return@forEach
            }

            if (existingTask != null) {
                if (shouldUpdateTask(existingTask, event)) {
                    updateTaskFromEvent(existingTask, event)
                }
            } else {
                createNewTaskFromEvent(event)
                count++
            }
        }
        return count
    }

    private suspend fun findMatchingTask(event: CalendarEvent): Task? {
        try {
            val task = taskDao.getTaskByCalendarEventIdIncludingDeleted(event.id)
            if (task != null) return task
        } catch (e: Exception) { /* ignore */ }

        val startOfDay = DateUtils.getStartOfDay(event.startTime)
        val endOfDay = startOfDay + (24 * 60 * 60 * 1000) - 1
        try {
            val task = taskDao.findTaskByTitleAndDate(event.title, startOfDay, endOfDay)
            if (task != null) return task
        } catch (e: Exception) { /* ignore */ }

        try {
            val task = taskDao.findUnscheduledTaskByTitle(event.title)
            if (task != null) return task
        } catch (e: Exception) { /* ignore */ }

        return null
    }

    private fun shouldUpdateTask(task: Task, event: CalendarEvent): Boolean {
        if (task.calendarEventId != event.id) return true
        val expectedReminderData = if (event.isAllDay) null else event.startTime
        return task.scheduledDate != event.startTime ||
               task.reminderTime != expectedReminderData ||
               task.isRecurring != event.isRecurring
    }

    private suspend fun updateTaskFromEvent(task: Task, event: CalendarEvent) {
        val updatedTask = task.copy(
            calendarEventId = event.id,
            scheduledDate = event.startTime,
            reminderTime = if (event.isAllDay) null else event.startTime,
            isRecurring = event.isRecurring
        )
        taskDao.updateTask(updatedTask)
        updatedTask.reminderTime?.let {
            reminderManager.scheduleReminder(updatedTask.id, updatedTask.content, it, updatedTask.priority.name)
        }
    }

    private suspend fun createNewTaskFromEvent(event: CalendarEvent) {
        val newTask = Task(
            content = event.title,
            scheduledDate = event.startTime,
            calendarEventId = event.id,
            priority = com.example.aitasklist.model.Priority.MEDIUM,
            isCompleted = false,
            reminderTime = if (event.isAllDay) null else event.startTime,
            isRecurring = event.isRecurring
        )
        taskDao.insertTasks(listOf(newTask))
        newTask.reminderTime?.let {
            reminderManager.scheduleReminder(newTask.id, newTask.content, it, newTask.priority.name)
        }
    }

    private suspend fun performPushSync(windowStart: Long, windowEnd: Long, allTasks: List<Task>): Int {
        val tasksToSync = allTasks.filter { 
            !it.isCompleted && 
            it.scheduledDate >= windowStart && 
            it.scheduledDate <= windowEnd &&
            it.calendarEventId == null
        }

        if (tasksToSync.isEmpty()) return 0

        var pushedCount = 0
        val updatedTasks = mutableListOf<Task>()
        val defaultDuration = AutoScheduler.DEFAULT_TASK_DURATION_MS
        
        // We need defaultCalendarId for this.
        // Ideally CalendarRepository handles "default" if null is passed, or we fetch it?
        // Let's fetch it from repository or assume passed?
        // The SyncManager might need to know the default calendar ID.
        // For now, let's fetch it per call or cache it?
        val defaultCalendarId = calendarRepository.getDefaultCalendarId()

        tasksToSync.forEach { task ->
            val endParams = task.scheduledDate + defaultDuration
            val result = calendarRepository.addToCalendar(
                title = task.content,
                description = "Synced from AI Task List",
                startTime = task.scheduledDate,
                endTime = endParams,
                calendarId = defaultCalendarId,
                isAllDay = task.reminderTime == null
            )
            if (result != null) {
                pushedCount++
                updatedTasks.add(task.copy(calendarEventId = result.first))
            }
        }
        if (updatedTasks.isNotEmpty()) {
            taskDao.updateTasks(updatedTasks)
        }
        return pushedCount
    }

    private suspend fun performAutoSchedule(
        windowStart: Long, 
        windowEnd: Long, 
        events: List<CalendarEvent>,
        unscheduledTasks: List<Task>
    ): Int {
        val busySlots = events.map { TimeSlot(it.startTime, it.endTime) }
        val gaps = calendarGapManager.findGaps(windowStart, windowEnd, busySlots)
        
        val todayNormalized = DateUtils.getStartOfDay(windowStart)
        val scheduledTasks = autoScheduler.scheduleTasks(unscheduledTasks, gaps, todayNormalized)
        
        if (scheduledTasks.isNotEmpty()) {
            val finalTasks = mutableListOf<Task>()
            val defaultCalendarId = calendarRepository.getDefaultCalendarId()
            
            scheduledTasks.forEach { task ->
                val duration = AutoScheduler.DEFAULT_TASK_DURATION_MS
                val startTime = task.scheduledDate 
                val endTime = startTime + duration
                val result = calendarRepository.addToCalendar(
                    title = task.content,
                    description = "Auto-Scheduled by AI Task List",
                    startTime = startTime,
                    endTime = endTime,
                    calendarId = defaultCalendarId,
                    isAllDay = false
                )

                val updatedTask = if (result != null) {
                    task.copy(calendarEventId = result.first) 
                } else {
                    task
                }
                
                updatedTask.reminderTime?.let { time ->
                    reminderManager.scheduleReminder(updatedTask.id, updatedTask.content, time, updatedTask.priority.name)
                }
                finalTasks.add(updatedTask)
            }
            taskDao.updateTasks(finalTasks)
            return finalTasks.size
        }
        return 0
    }
}

data class SyncResult(
    val eventsCount: Int,
    val gapsCount: Int,
    val scheduledCount: Int,
    val importedCount: Int,
    val pushedCount: Int
)
