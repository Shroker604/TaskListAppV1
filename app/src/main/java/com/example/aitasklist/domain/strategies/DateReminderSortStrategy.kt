package com.example.aitasklist.domain.strategies

import com.example.aitasklist.domain.SortStrategy
import com.example.aitasklist.model.Task
import com.example.aitasklist.util.DateUtils

class DateReminderSortStrategy : SortStrategy {
    override fun sort(tasks: List<Task>, ascending: Boolean): List<Task> {
        val comparator = Comparator<Task> { t1, t2 ->
            // 1. Completion Status (Active first)
            if (t1.isCompleted != t2.isCompleted) {
                if (t1.isCompleted) 1 else -1
            } else {
                // 2. Scheduled Date (Start of Day)
                // Treat 0L as "No Date", effectively infinite future or separate category
                val date1 = if (t1.scheduledDate == 0L) Long.MAX_VALUE else DateUtils.getStartOfDay(t1.scheduledDate)
                val date2 = if (t2.scheduledDate == 0L) Long.MAX_VALUE else DateUtils.getStartOfDay(t2.scheduledDate)
                
                val dateResult = if (ascending) {
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
                        if (ascending) {
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
        return tasks.sortedWith(comparator)
    }
}
