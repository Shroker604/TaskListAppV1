package com.example.aitasklist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitasklist.TaskViewModel
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.aitasklist.model.Task
import android.content.Intent
import android.provider.CalendarContract
import android.content.ContentUris

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.geometry.Offset
import com.example.aitasklist.SortOption
import com.example.aitasklist.R

@Composable
fun TaskListScreen(
    viewModel: TaskViewModel = viewModel(),
    isDarkTheme: Boolean = false,
    onThemeToggle: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var textInput by remember { mutableStateOf("") }
    var showCalendarDialog by remember { mutableStateOf(false) }
    var isSplitInputEnabled by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        viewModel.loadCalendars()
    }
    
    val context = LocalContext.current
    var pendingCalendarTask by remember { mutableStateOf<Task?>(null) }
    
    // Reminder state
    var timePickerDialog by remember { mutableStateOf<android.app.TimePickerDialog?>(null) }
    var taskIdForReminder by remember { mutableStateOf<String?>(null) }

    val calendarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[android.Manifest.permission.READ_CALENDAR] == true
        val writeGranted = permissions[android.Manifest.permission.WRITE_CALENDAR] == true

        if (readGranted && writeGranted && pendingCalendarTask != null) {
            viewModel.addToCalendar(pendingCalendarTask!!) { accountName ->
                Toast.makeText(context, context.getString(R.string.added_to_calendar, accountName), Toast.LENGTH_SHORT).show()
                pendingCalendarTask = null
            }
        } else {
            // Only show toast if it was a calendar request (pending task set)
            if (pendingCalendarTask != null) {
                Toast.makeText(context, context.getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
           // Optional: Show rationale
        }
    }
    
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }




    // Hoisted state for reordering
    var localTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    
    // Update localTasks when uiState.tasks changes, efficiently
    LaunchedEffect(uiState.tasks) {
        if (localTasks.isEmpty() || uiState.sortOption != SortOption.CUSTOM) {
             localTasks = uiState.tasks
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_header),
                style = MaterialTheme.typography.headlineMedium
            )
            Row {
                if (uiState.sortOption == SortOption.CUSTOM) {
                    IconButton(onClick = { 
                        // Cancel - revert to default sort
                        viewModel.setSortOption(SortOption.CREATION_DATE)
                        localTasks = uiState.tasks // Reset local changes
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Reorder")
                    }
                    IconButton(onClick = { 
                        // Done - save order
                        viewModel.updateAllTasksOrder(localTasks)
                        viewModel.setSortOption(SortOption.CREATION_DATE)
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save Reorder")
                    }
                } else {
                    Box {
                        var showSortMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Sort Tasks"
                            )
                        }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("By Creation Date") },
                            onClick = {
                                viewModel.setSortOption(SortOption.CREATION_DATE)
                                showSortMenu = false
                            },
                            trailingIcon = if (uiState.sortOption == SortOption.CREATION_DATE) {
                                { 
                                    Icon(
                                        if (uiState.sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, 
                                        contentDescription = null
                                    ) 
                                }
                            } else null
                        )
                        DropdownMenuItem(
                            text = { Text("By Date & Reminder") },
                            onClick = {
                                viewModel.setSortOption(SortOption.DATE_REMINDER)
                                showSortMenu = false
                            },
                            trailingIcon = if (uiState.sortOption == SortOption.DATE_REMINDER) {
                                { 
                                    Icon(
                                        if (uiState.sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, 
                                        contentDescription = null
                                    ) 
                                }
                            } else null
                        )
                        if (uiState.sortOption == SortOption.CUSTOM) {
                            DropdownMenuItem(
                                text = { Text("Custom Order", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                                onClick = { },
                                enabled = false,
                                trailingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) }
                            )
                        }
                    }
                }
            }

                Box {
                    var showMenu by remember { mutableStateOf(false) }
                    
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_desc))
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isDarkTheme) "Light Mode" else "Dark Mode") },
                            onClick = {
                                onThemeToggle()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Calendars") },
                            onClick = {
                                showCalendarDialog = true
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = "Select Calendar"
                                )
                            }
                        )
                    }
                }
            }
        }

        if (showCalendarDialog) {
            CalendarSelectionDialog(
                calendars = uiState.calendars,
                defaultCalendarId = uiState.defaultCalendarId,
                onCalendarSelected = { viewModel.setDefaultCalendar(it) },
                onDismiss = { showCalendarDialog = false }
            )
        }

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            label = { Text(stringResource(R.string.enter_plans_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { 
                        viewModel.generateTasks(textInput, isSplitInputEnabled)
                        textInput = "" 
                    },
                    enabled = !uiState.isLoading
                ) {
                    Text(stringResource(R.string.generate_tasks))
                }

                Button(
                    onClick = { isSplitInputEnabled = !isSplitInputEnabled },
                    border = if (isSplitInputEnabled) BorderStroke(2.dp, androidx.compose.ui.graphics.Color.Green) else null
                ) {
                    Text("Split")
                }
            }

            TextButton(
                onClick = { viewModel.removeCompletedTasks() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.remove_completed))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (uiState.error != null) {
            Text(
                text = stringResource(R.string.error_prefix, uiState.error ?: ""),
                color = MaterialTheme.colorScheme.error
            )
        } else {
            val tasks = uiState.tasks
            val sortOption = uiState.sortOption
            
            if (sortOption == SortOption.CUSTOM) {
                 val listState = rememberScrollState()
                 var draggedIndex by remember { mutableStateOf<Int?>(null) }
                 var dragOffset by remember { mutableStateOf(0f) }
                 
                 Column(
                     modifier = Modifier
                         .weight(1f)
                         .verticalScroll(listState)
                         .padding(vertical = 8.dp),
                     verticalArrangement = Arrangement.spacedBy(8.dp)
                 ) {
                     localTasks.forEachIndexed { index, task ->
                         key(task.id) {
                             TaskItem(
                                 task = task,
                                 onCheckedChange = { viewModel.toggleTaskCompletion(task.id) },
                                 onDateChange = { newDate -> viewModel.updateTaskDate(task.id, newDate) },
                                 onAddToCalendar = {
                                     pendingCalendarTask = task
                                     if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                                         viewModel.addToCalendar(task) { accountName ->
                                             Toast.makeText(context, context.getString(R.string.added_to_calendar, accountName), Toast.LENGTH_SHORT).show()
                                             pendingCalendarTask = null
                                         }
                                     } else {
                                         calendarLauncher.launch(arrayOf(
                                             android.Manifest.permission.READ_CALENDAR,
                                             android.Manifest.permission.WRITE_CALENDAR
                                         ))
                                     }
                                 },
                                 onPriorityChange = { newPriority -> viewModel.updateTaskPriority(task.id, newPriority) },
                                 onOpenCalendar = {
                                     viewModel.openCalendarEvent(
                                         task = task,
                                         onSuccess = { uri ->
                                              val intent = Intent(Intent.ACTION_VIEW).apply {
                                                 data = uri
                                             }
                                             try {
                                                 context.startActivity(intent)
                                             } catch (e: Exception) {
                                                 Toast.makeText(context, context.getString(R.string.could_not_open_calendar), Toast.LENGTH_SHORT).show()
                                             }
                                         },
                                         onError = {
                                             Toast.makeText(context, context.getString(R.string.event_not_found_resync), Toast.LENGTH_SHORT).show()
                                         }
                                     )
                                 },
                                 onSetReminder = {
                                     taskIdForReminder = task.id
                                     val cal = java.util.Calendar.getInstance()
                                     if (task.reminderTime != null) {
                                         cal.timeInMillis = task.reminderTime
                                     }
                                     timePickerDialog = android.app.TimePickerDialog(
                                         context,
                                         { _, hourOfDay, minute ->
                                             val newCal = java.util.Calendar.getInstance()
                                             newCal.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                                             newCal.set(java.util.Calendar.MINUTE, minute)
                                             newCal.set(java.util.Calendar.SECOND, 0)
                                             
                                             if (newCal.timeInMillis <= System.currentTimeMillis()) {
                                                 newCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                             }
                                             
                                             taskIdForReminder?.let { id ->
                                                 viewModel.updateTaskReminder(id, newCal.timeInMillis)
                                             }
                                             taskIdForReminder = null
                                         },
                                         cal.get(java.util.Calendar.HOUR_OF_DAY),
                                         cal.get(java.util.Calendar.MINUTE),
                                         false 
                                     )
                                     timePickerDialog?.show()
                                 },
                            showDragHandle = true,
                            dragModifier = Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { 
                                        draggedIndex = localTasks.indexOfFirst { it.id == task.id }
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y
                                        
                                        val currentIndex = draggedIndex ?: return@detectDragGestures
                                        val threshold = 100f 
                                        
                                        if (dragOffset > threshold) {
                                            if (currentIndex < localTasks.size - 1) {
                                                val currentList = localTasks.toMutableList()
                                                java.util.Collections.swap(currentList, currentIndex, currentIndex + 1)
                                                localTasks = currentList
                                                draggedIndex = currentIndex + 1 
                                                dragOffset = 0f 
                                            }
                                        } else if (dragOffset < -threshold) {
                                            if (currentIndex > 0) {
                                                val currentList = localTasks.toMutableList()
                                                java.util.Collections.swap(currentList, currentIndex, currentIndex - 1)
                                                localTasks = currentList
                                                draggedIndex = currentIndex - 1
                                                dragOffset = 0f
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggedIndex = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggedIndex = null
                                        dragOffset = 0f
                                    }
                                )
                            },
                        onEnterReorderMode = {
                            viewModel.setSortOption(SortOption.CUSTOM)
                        }
                        )
                    }
                }
            }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Split tasks for display
                    val scheduledTasks = uiState.tasks.filter { it.scheduledDate != 0L || it.reminderTime != null }
                    val otherTasks = uiState.tasks.filter { it.scheduledDate == 0L && it.reminderTime == null }

                    if (scheduledTasks.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(
                                    text = "Schedule",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            }
                        }
                        items(scheduledTasks, key = { it.id }) { task ->
                            TaskItem(
                                task = task,
                                onCheckedChange = { viewModel.toggleTaskCompletion(task.id) },
                                onDateChange = { newDate -> viewModel.updateTaskDate(task.id, newDate) },
                                onAddToCalendar = {
                                    pendingCalendarTask = task
                                    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.addToCalendar(task) { accountName ->
                                            Toast.makeText(context, context.getString(R.string.added_to_calendar, accountName), Toast.LENGTH_SHORT).show()
                                            pendingCalendarTask = null
                                        }
                                    } else {
                                        calendarLauncher.launch(arrayOf(
                                            android.Manifest.permission.READ_CALENDAR,
                                            android.Manifest.permission.WRITE_CALENDAR
                                        ))
                                    }
                                },
                                onPriorityChange = { newPriority -> viewModel.updateTaskPriority(task.id, newPriority) },
                                onOpenCalendar = {
                                    viewModel.openCalendarEvent(
                                        task = task,
                                        onSuccess = { uri ->
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                data = uri
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, context.getString(R.string.could_not_open_calendar), Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onError = {
                                            Toast.makeText(context, context.getString(R.string.event_not_found_resync), Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                onSetReminder = {
                                    taskIdForReminder = task.id
                                    val cal = java.util.Calendar.getInstance()
                                    if (task.reminderTime != null) {
                                        cal.timeInMillis = task.reminderTime
                                    }
                                    timePickerDialog = android.app.TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            val newCal = java.util.Calendar.getInstance()
                                            newCal.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                                            newCal.set(java.util.Calendar.MINUTE, minute)
                                            newCal.set(java.util.Calendar.SECOND, 0)
                                            
                                            if (newCal.timeInMillis <= System.currentTimeMillis()) {
                                                newCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                            }
                                            
                                            taskIdForReminder?.let { id ->
                                                viewModel.updateTaskReminder(id, newCal.timeInMillis)
                                            }
                                            taskIdForReminder = null
                                        },
                                        cal.get(java.util.Calendar.HOUR_OF_DAY),
                                            cal.get(java.util.Calendar.MINUTE),
                                            true 
                                        )
                                        timePickerDialog?.show()
                                },
                                onEnterReorderMode = {
                                    viewModel.setSortOption(SortOption.CUSTOM)
                                }
                            )
                        }
                    }

                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(
                                text = "Tasks",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        }
                    }

                    items(otherTasks, key = { it.id }) { task ->
                        TaskItem(
                            task = task,
                            onCheckedChange = { viewModel.toggleTaskCompletion(task.id) },
                            onDateChange = { newDate -> viewModel.updateTaskDate(task.id, newDate) },
                            onAddToCalendar = {
                                pendingCalendarTask = task
                                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.addToCalendar(task) { accountName ->
                                        Toast.makeText(context, context.getString(R.string.added_to_calendar, accountName), Toast.LENGTH_SHORT).show()
                                        pendingCalendarTask = null
                                    }
                                } else {
                                    calendarLauncher.launch(arrayOf(
                                        android.Manifest.permission.READ_CALENDAR,
                                        android.Manifest.permission.WRITE_CALENDAR
                                    ))
                                }
                            },
                            onPriorityChange = { newPriority -> viewModel.updateTaskPriority(task.id, newPriority) },
                            onOpenCalendar = {
                                viewModel.openCalendarEvent(
                                    task = task,
                                    onSuccess = { uri ->
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            data = uri
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, context.getString(R.string.could_not_open_calendar), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onError = {
                                        Toast.makeText(context, context.getString(R.string.event_not_found_resync), Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            onSetReminder = {
                                taskIdForReminder = task.id
                                val cal = java.util.Calendar.getInstance()
                                if (task.reminderTime != null) {
                                    cal.timeInMillis = task.reminderTime
                                }
                                timePickerDialog = android.app.TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        val newCal = java.util.Calendar.getInstance()
                                        newCal.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                                        newCal.set(java.util.Calendar.MINUTE, minute)
                                        newCal.set(java.util.Calendar.SECOND, 0)
                                        
                                        if (newCal.timeInMillis <= System.currentTimeMillis()) {
                                            newCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                        }
                                        
                                        taskIdForReminder?.let { id ->
                                            viewModel.updateTaskReminder(id, newCal.timeInMillis)
                                        }
                                        taskIdForReminder = null
                                    },
                                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                                        cal.get(java.util.Calendar.MINUTE),
                                        true 
                                    )
                                    timePickerDialog?.show()
                            },
                            onEnterReorderMode = {
                                viewModel.setSortOption(SortOption.CUSTOM)
                            }
                        )
                    }
                }
            }
        }
    }
}
