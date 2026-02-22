package com.example.aitasklist.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aitasklist.model.Task
import java.util.Calendar

@Composable
fun DailyPlannerList(
    tasks: List<Task>,
    bindTaskItem: @Composable (Task, Boolean, Modifier?) -> Unit
) {
    // Group tasks by Date: 
    // Overdue, Today, Tomorrow, Current Week (Rest of week), Next Week, Upcoming, No Date
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxHeight() // Takes available space in column
    ) {
        val today = Calendar.getInstance()
        
        // Normalize today to start of day for comparison
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val tomorrow = Calendar.getInstance().apply { 
            timeInMillis = today.timeInMillis
            add(Calendar.DAY_OF_YEAR, 1) 
        }
        
        // End of Current Week (Saturday)
        val endOfCurrentWeek = Calendar.getInstance().apply {
            timeInMillis = today.timeInMillis
            set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
            if (get(Calendar.DAY_OF_YEAR) < today.get(Calendar.DAY_OF_YEAR)) {
                 add(Calendar.WEEK_OF_YEAR, 1)
            }
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }

        // End of Next Week
        val startOfNextWeek = Calendar.getInstance().apply {
            timeInMillis = endOfCurrentWeek.timeInMillis
            add(Calendar.DAY_OF_YEAR, 1) // Sunday
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }
        
        val endOfNextWeek = Calendar.getInstance().apply {
            timeInMillis = startOfNextWeek.timeInMillis
            add(Calendar.DAY_OF_YEAR, 6) // Next Saturday
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }
        
        val groupedTasks = tasks.groupBy { task ->
            if (task.scheduledDate == 0L) "No Date"
            else {
                val taskCal = Calendar.getInstance().apply { timeInMillis = task.scheduledDate }
                // Normalize task time to start of day for accurate day-comparison
                val taskStartOfDay = Calendar.getInstance().apply {
                    timeInMillis = task.scheduledDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                if (taskStartOfDay.timeInMillis < today.timeInMillis) {
                    "Overdue"
                } else if (taskStartOfDay.timeInMillis == today.timeInMillis) {
                    // Check if time-overdue (Today + Late)
                    if (task.reminderTime != null && task.reminderTime < System.currentTimeMillis()) {
                        "Overdue"
                    } else {
                        "Today"
                    }
                } else if (taskStartOfDay.timeInMillis == tomorrow.timeInMillis) {
                    "Tomorrow"
                } else if (taskCal.timeInMillis <= endOfCurrentWeek.timeInMillis) {
                    "Current Week"
                } else if (taskCal.timeInMillis <= endOfNextWeek.timeInMillis) {
                    "Next Week"
                } else {
                    "Upcoming"
                }
            }
        }

        // Order: Overdue, Today, Tomorrow, Current Week, Next Week, Upcoming, No Date
        val sectionOrder = listOf("Overdue", "Today", "Tomorrow", "Current Week", "Next Week", "Upcoming", "No Date")
        
        sectionOrder.forEach { section ->
            groupedTasks[section]?.let { sectionTasks ->
                 if (sectionTasks.isNotEmpty()) {
                     item {
                         Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                             Text(
                                 text = section,
                                 style = MaterialTheme.typography.titleMedium,
                                 color = if(section == "Overdue") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                 modifier = Modifier.padding(bottom = 4.dp)
                             )
                             Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                         }
                     }
                     items(sectionTasks, key = { it.id }) { task ->
                         bindTaskItem(task, false, null)
                     }
                 }
            }
        }
    }
}
