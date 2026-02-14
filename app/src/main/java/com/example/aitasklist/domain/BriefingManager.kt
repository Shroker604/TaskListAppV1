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

    // For Worker/overlay usage: Fetch from DAO directly
    suspend fun getBriefingFromDao(now: Long, taskDao: TaskDao): HourlyBriefing = withContext(Dispatchers.IO) {
        val oneHourLater = now + 3600000
        val endOfDay = DateUtils.getEndOfDay(now)

        // Parallel execution could be better but sequential is fine for SQLite
        val overdue = taskDao.getOverdueTasks(now)
        val nextHour = taskDao.getTasksInRange(now, oneHourLater)
        val restOfDay = taskDao.getTasksInRange(oneHourLater + 1, endOfDay)
        val unscheduled = taskDao.getUnscheduledTasks()
        
        HourlyBriefing(
            overdueTasks = overdue,
            nextHourTasks = nextHour,
            restOfDayTasks = restOfDay,
            unscheduledTasks = unscheduled
        )
    }
}

data class HourlyBriefing(
    val overdueTasks: List<Task>,
    val nextHourTasks: List<Task>,
    val restOfDayTasks: List<Task>,
    val unscheduledTasks: List<Task>
)
