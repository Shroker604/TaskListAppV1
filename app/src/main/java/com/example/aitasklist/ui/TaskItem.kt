package com.example.aitasklist.ui


import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.res.stringResource
import com.example.aitasklist.R
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.clickable

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.aitasklist.model.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskItem(
    task: Task,
    onCheckedChange: (Boolean) -> Unit,
    onDateChange: (Long) -> Unit,
    onAddToCalendar: () -> Unit,
    onOpenCalendar: () -> Unit,
    onSetReminder: () -> Unit,
    onPriorityChange: (com.example.aitasklist.model.Priority) -> Unit,
    showDragHandle: Boolean = false,
    dragModifier: Modifier = Modifier,
    onEnterReorderMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    // ... rest of init code

    calendar.timeInMillis = task.scheduledDate

    val currentCalendar = Calendar.getInstance()
    val taskCalendar = Calendar.getInstance()
    taskCalendar.timeInMillis = task.scheduledDate

    var showContextMenu by remember { mutableStateOf(false) }

    val isOverdue = !task.isCompleted && (
            taskCalendar.get(Calendar.YEAR) < currentCalendar.get(Calendar.YEAR) ||
                    (taskCalendar.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) &&
                            taskCalendar.get(Calendar.DAY_OF_YEAR) < currentCalendar.get(Calendar.DAY_OF_YEAR))
            )


    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = remember(task.scheduledDate) {
                val cal = Calendar.getInstance()
                if (task.scheduledDate != 0L) {
                    cal.timeInMillis = task.scheduledDate
                } else {
                    cal.timeInMillis = System.currentTimeMillis()
                }
                val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                utcCal.clear()
                utcCal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                utcCal.timeInMillis
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        utcCal.timeInMillis = utcMillis
                        
                        val localCal = Calendar.getInstance()
                        if (task.scheduledDate != 0L) {
                            localCal.timeInMillis = task.scheduledDate
                        } else {
                            localCal.timeInMillis = System.currentTimeMillis()
                        }
                        localCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH))
                        onDateChange(localCal.timeInMillis)
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { 
                    if (!showDragHandle) {
                        showContextMenu = true 
                    }
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
             DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Priority: ${task.priority.name}") },
                    onClick = {
                        showContextMenu = false
                        // Cycle priority logic: LOW -> MEDIUM -> HIGH -> LOW
                        val newPriority = when (task.priority) {
                             com.example.aitasklist.model.Priority.LOW -> com.example.aitasklist.model.Priority.MEDIUM
                             com.example.aitasklist.model.Priority.MEDIUM -> com.example.aitasklist.model.Priority.HIGH
                             com.example.aitasklist.model.Priority.HIGH -> com.example.aitasklist.model.Priority.LOW
                        }
                        onPriorityChange(newPriority)
                    },
                    leadingIcon = {
                        Icon(
                             imageVector = Icons.Default.Star,
                             contentDescription = null,
                             tint = when (task.priority) {
                                 com.example.aitasklist.model.Priority.HIGH -> androidx.compose.ui.graphics.Color.Red
                                 com.example.aitasklist.model.Priority.MEDIUM -> androidx.compose.ui.graphics.Color.Yellow
                                 else -> MaterialTheme.colorScheme.onSurface
                             }
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Set/Edit Reminder") },
                    onClick = {
                        showContextMenu = false
                        onSetReminder()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Reorder Tasks") },
                    onClick = {
                        showContextMenu = false
                        onEnterReorderMode()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.List, contentDescription = null)
                    }
                )
            }
            
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showDragHandle) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = dragModifier
                            .size(24.dp)
                            .padding(end = 8.dp)
                    )
                }

                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = onCheckedChange,
                    enabled = !showDragHandle
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.content,
                        style = if (task.isCompleted) {
                            MaterialTheme.typography.bodyLarge.copy(
                                textDecoration = TextDecoration.LineThrough,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            MaterialTheme.typography.bodyLarge
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                         if (task.priority != com.example.aitasklist.model.Priority.LOW) {
                             Text(
                                 text = if (task.priority == com.example.aitasklist.model.Priority.HIGH) "!!!" else "!!",
                                 style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                 color = if (task.priority == com.example.aitasklist.model.Priority.HIGH) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color(0xFFFFA000), // Orange/Amber
                                 modifier = Modifier.padding(end = 4.dp)
                             )
                         }
                         if (task.scheduledDate != 0L) {
                            Text(
                                text = dateFormat.format(Date(task.scheduledDate)),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        if (task.reminderTime != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Reminder set",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = timeFormat.format(Date(task.reminderTime)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                IconButton(onClick = { showDatePicker = true }, enabled = !showDragHandle) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = stringResource(R.string.change_date_desc),
                        tint = if (!showDragHandle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                if (task.calendarEventId == null) {
                    IconButton(onClick = onAddToCalendar, enabled = !showDragHandle) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_to_calendar_desc),
                            tint = if (!showDragHandle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                } else {
                    IconButton(onClick = onOpenCalendar, enabled = !showDragHandle) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.view_in_calendar_desc),
                            tint = if (!showDragHandle) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    }
}
