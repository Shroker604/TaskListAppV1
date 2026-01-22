package com.example.aitasklist.domain

import com.example.aitasklist.model.Task
import com.example.aitasklist.util.DateUtils

enum class SortOption {
    CREATION_DATE, // Default: Newest first
    DATE_REMINDER,  // Scheduled Date -> Reminder Time
    CUSTOM         // User defined order
}

object TaskSorter {
    fun sortTasks(tasks: List<Task>, sortOption: SortOption, sortAscending: Boolean): List<Task> {
        return when (sortOption) {
            SortOption.CREATION_DATE -> {
                val comparator = Comparator<Task> { t1, t2 ->
                    // 1. Completion Status (Active first)
                    if (t1.isCompleted != t2.isCompleted) {
                        if (t1.isCompleted) 1 else -1
                    } else {
                        // 2. Creation Date
                        if (sortAscending) {
                            t1.createdAt.compareTo(t2.createdAt)
                        } else {
                            t2.createdAt.compareTo(t1.createdAt)
                        }
                    }
                }
                tasks.sortedWith(comparator)
            }
            SortOption.DATE_REMINDER -> {
                val comparator = Comparator<Task> { t1, t2 ->
                    // 1. Completion Status (Active first)
                    if (t1.isCompleted != t2.isCompleted) {
                        if (t1.isCompleted) 1 else -1
                    } else {
                        // 2. Scheduled Date (Start of Day)
                        // Treat 0L as "No Date", effectively infinite future or separate category
                        val date1 = if (t1.scheduledDate == 0L) Long.MAX_VALUE else DateUtils.getStartOfDay(t1.scheduledDate)
                        val date2 = if (t2.scheduledDate == 0L) Long.MAX_VALUE else DateUtils.getStartOfDay(t2.scheduledDate)
                        
                        val dateResult = if (sortAscending) {
                            date1.compareTo(date2)
                        } else {
                            date2.compareTo(date1)
                        }

                        if (dateResult != 0) {
                             dateResult 
                        } else {
                            // 3. Priority: Task with reminder comes first
                            val hasReminder1 = t1.reminderTime != null
                            val hasReminder2 = t2.reminderTime != null
                            
                            if (hasReminder1 && !hasReminder2) -1
                            else if (!hasReminder1 && hasReminder2) 1
                            else if (hasReminder1 && hasReminder2) {
                                // Both have reminders, compare time respecting sort direction
                                if (sortAscending) {
                                    t1.reminderTime!!.compareTo(t2.reminderTime!!)
                                } else {
                                    t2.reminderTime!!.compareTo(t1.reminderTime!!)
                                }
                            } else {
                                // Neither has reminder, keep stable (or sort by creation?)
                                // Let's use ID for stability or creation date
                                t2.createdAt.compareTo(t1.createdAt) // Newest created first within group
                            }
                        }
                    }
                }
                tasks.sortedWith(comparator)
            }
            SortOption.CUSTOM -> {
                tasks.sortedBy { it.orderIndex }
            }
        }
    }
}
