package com.example.aitasklist.domain

import com.example.aitasklist.model.Task
import com.example.aitasklist.data.local.TaskDao
import com.example.aitasklist.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BriefingManager(
    private val taskDao: TaskDao? = null // Optional for worker usage where it might fetch fresh
) {

    fun getBriefingForTasks(
        now: Long,
        allTasks: List<Task>
    ): HourlyBriefing {
        val tasks = allTasks.filter { !it.isCompleted && !it.isDeleted }
        val oneHourLater = now + 3600000
        val endOfDay = DateUtils.getEndOfDay(now)

        val overdue = tasks.filter {
            it.scheduledDate > 0L && it.scheduledDate < now
        }.sortedBy { it.scheduledDate }

        val nextHour = tasks.filter { 
            it.scheduledDate in now..oneHourLater 
        }.sortedBy { it.scheduledDate }

        val restOfDay = tasks.filter { 
            it.scheduledDate > oneHourLater && it.scheduledDate <= endOfDay
        }.sortedBy { it.scheduledDate }

        val unscheduled = tasks.filter { 
            it.scheduledDate == 0L 
        }

        return HourlyBriefing(overdue, nextHour, restOfDay, unscheduled)
    }

    // For Worker usage: Fetch from DAO directly
    suspend fun getBriefingFromDao(now: Long, taskDao: TaskDao): HourlyBriefing = withContext(Dispatchers.IO) {
        // Optimally, we would query efficiently, but for Consistency with ViewModel logic, 
        // we can fetch relevant ranges or just use the logic above if we fetch "Active Tasks".
        // Fetching all active tasks might be heavy, but safer for consistency.
        // Or we use specific queries as before.
        
        // Let's use the optimized queries provided in DAO which are better for background workers.
        val oneHourLater = now + 3600000
        val endOfDay = DateUtils.getStartOfDay(now) + (24 * 60 * 60 * 1000) - 1

        val nextHour = taskDao.getTasksInRange(now, oneHourLater)
        val restOfDay = taskDao.getTasksInRange(oneHourLater + 1, endOfDay)
        
        // Overdue?
        // Worker previously didn't check overdue. Let's keep it consistent with previous worker logic 
        // OR upgrade it to match ViewModel? 
        // The implementation plan said "Centralize... logic". 
        // ViewModel's logic is richer (includes overdue). Worker's was lighter.
        // Let's allow Worker to benefit from Richer logic if possible, or stick to efficient sub-queries.
        
        HourlyBriefing(
            overdueTasks = emptyList(), // Worker didn't check this before, maybe add later?
            nextHourTasks = nextHour,
            restOfDayTasks = restOfDay,
            unscheduledTasks = emptyList()
        )
    }
}

data class HourlyBriefing(
    val overdueTasks: List<Task>,
    val nextHourTasks: List<Task>,
    val restOfDayTasks: List<Task>,
    val unscheduledTasks: List<Task>
)
