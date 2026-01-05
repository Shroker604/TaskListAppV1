package com.example.aitasklist.scheduler

import com.example.aitasklist.model.Priority
import com.example.aitasklist.model.Task

class AutoScheduler {

    companion object {
        const val DEFAULT_TASK_DURATION_MS = 15 * 60 * 1000L // 15 Minutes
        const val MIN_GAP_SIZE_MS = 15 * 60 * 1000L // Minimum gap to consider usable
    }

    /**
     * Schedules tasks into the provided gaps.
     * @param tasks List of unscheduled tasks
     * @param gaps List of available time slots
     * @param scheduledDate The date (millis) to set for the tasks (Start of Day or similar logic handled by caller)
     * @return List of Updated Tasks with new reminder times
     */
    fun scheduleTasks(
        tasks: List<Task>,
        gaps: List<TimeSlot>,
        scheduledDate: Long
    ): List<Task> {
        // 1. Sort Tasks by Priority (High -> Low), then by OrderIndex/Creation
        val sortedTasks = tasks.sortedWith(
            compareByDescending<Task> { 
                when(it.priority) {
                    Priority.HIGH -> 3
                    Priority.MEDIUM -> 2
                    Priority.LOW -> 1
                }
            }.thenBy { it.orderIndex }
        )

        val scheduledTasks = mutableListOf<Task>()
        // Working copy of gaps because we will "consume" them
        val availableGaps = gaps.toMutableList()

        for (task in sortedTasks) {
            // Find first gap that fits the default duration
            val gapIndex = availableGaps.indexOfFirst { it.durationMillis >= DEFAULT_TASK_DURATION_MS }
            
            if (gapIndex != -1) {
                val gap = availableGaps[gapIndex]
                
                // Schedule Task
                val newReminderTime = gap.start
                val scheduledTask = task.copy(
                    scheduledDate = scheduledDate,
                    reminderTime = newReminderTime
                )
                scheduledTasks.add(scheduledTask)

                // Consume space in the gap
                val consumedDuration = DEFAULT_TASK_DURATION_MS
                val newGapStart = gap.start + consumedDuration

                if (newGapStart < gap.end) {
                    // Update the gap with remaining time
                    availableGaps[gapIndex] = TimeSlot(newGapStart, gap.end)
                } else {
                    // Gap fully consumed (or remaining is negligible if we had strict fit, but here we just used default)
                    availableGaps.removeAt(gapIndex)
                }
            } else {
                // No gap found for this task
                // Stop? Or continue trying smaller tasks if we had variable duration?
                // For MVP, if no 15m gap exists, we can't schedule.
            }
        }

        return scheduledTasks
    }
}
