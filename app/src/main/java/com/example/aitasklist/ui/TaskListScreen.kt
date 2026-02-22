package com.example.aitasklist.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitasklist.R
import com.example.aitasklist.domain.SortOption
import com.example.aitasklist.TaskViewModel
import com.example.aitasklist.model.Task
import com.example.aitasklist.ui.components.TaskHeader
import com.example.aitasklist.ui.components.TaskInputSection
import com.example.aitasklist.ui.components.*
import java.util.Collections

@Composable
fun TaskListScreen(
    viewModel: TaskViewModel = viewModel(),
    isDarkTheme: Boolean = false,
    onThemeToggle: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var textInput by remember { mutableStateOf("") }
    var showCalendarDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadCalendars()
    }
    
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearUserMessage()
        }
    }
    
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    // Reminder state
    var timePickerDialog by remember { mutableStateOf<android.app.TimePickerDialog?>(null) }
    var taskIdForReminder by remember { mutableStateOf<String?>(null) }

    val calendarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[android.Manifest.permission.READ_CALENDAR] == true
        val writeGranted = permissions[android.Manifest.permission.WRITE_CALENDAR] == true

        if (readGranted && writeGranted) {
            pendingAction?.invoke()
            pendingAction = null
        } else {
             Toast.makeText(context, context.getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
             pendingAction = null
        }
    }
    
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Optional: Show rationale
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
    
    var showEditDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<Task?>(null) }

    // --- Helper for binding TaskItem logic ---
    // This allows us to define the complex logic once and reuse it in the loops below
    val bindTaskItem: @Composable (Task, Boolean, Modifier?) -> Unit = { task, isSortableMode, dragModifier ->
        TaskItem(
            task = task,
            onCheckedChange = { viewModel.toggleTaskCompletion(task.id) },
            onUpdateSchedule = { newDate, newTime -> viewModel.updateTaskSchedule(task.id, newDate, newTime) },
            onAddToCalendar = {
                val action = {
                    viewModel.addToCalendar(task) { accountName ->
                        Toast.makeText(context, context.getString(R.string.added_to_calendar, accountName), Toast.LENGTH_SHORT).show()
                    }
                }
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                    action()
                } else {
                    pendingAction = action
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
                // Initial Time for Picker:
                // 1. Existing Reminder Time
                // 2. Current Time (default for new reminder)
                if (task.reminderTime != null) {
                    cal.timeInMillis = task.reminderTime
                }
                
                timePickerDialog = android.app.TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        // Base the new reminder date on existing Task Date or Today
                        val newCal = java.util.Calendar.getInstance()
                        
                        if (task.scheduledDate != 0L) {
                             val taskDate = java.util.Calendar.getInstance()
                             taskDate.timeInMillis = task.scheduledDate
                             newCal.set(java.util.Calendar.YEAR, taskDate.get(java.util.Calendar.YEAR))
                             newCal.set(java.util.Calendar.MONTH, taskDate.get(java.util.Calendar.MONTH))
                             newCal.set(java.util.Calendar.DAY_OF_MONTH, taskDate.get(java.util.Calendar.DAY_OF_MONTH))
                        }
                        
                        newCal.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                        newCal.set(java.util.Calendar.MINUTE, minute)
                        newCal.set(java.util.Calendar.SECOND, 0)
                        
                        // Default behavior: Do NOT auto-increment to tomorrow.
                        // Reminder is strictly for the Task Date (or Today if no date).
                        
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
            },
            onEditTask = {
               editingTask = task
               showEditDialog = true
            },
            showDragHandle = isSortableMode,
            dragModifier = dragModifier ?: Modifier
        )
    }

    val isDailyPlanner by viewModel.isDailyPlanner.collectAsState()

    // ... (helper logic)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TaskHeader(
            sortOption = uiState.sortOption,
            sortAscending = uiState.sortAscending,
            isDarkTheme = isDarkTheme,
            onSortOptionSelected = { viewModel.setSortOption(it) },
            onSortOrderToggle = { /* handled by setSortOption logic in VM */ },
            onSaveReorder = {
                viewModel.updateAllTasksOrder(localTasks)
                viewModel.setSortOption(SortOption.CREATION_DATE)
            },
            onCancelReorder = {
                viewModel.setSortOption(SortOption.CREATION_DATE)
                localTasks = uiState.tasks
            },
            onThemeToggle = onThemeToggle,
            onOpenCalendarDialog = { showCalendarDialog = true },
            onOpenFilterDialog = { showFilterDialog = true },
            onAutoSchedule = { 
                val action = { viewModel.autoScheduleToday() }
                 if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                    action()
                } else {
                    pendingAction = action
                     calendarLauncher.launch(arrayOf(
                        android.Manifest.permission.READ_CALENDAR,
                        android.Manifest.permission.WRITE_CALENDAR
                    ))
                }
            },
            onOpenRestoreDialog = { showRestoreDialog = true },
            isDailyPlanner = isDailyPlanner,
            onToggleDailyPlanner = { viewModel.setDailyPlanner(!isDailyPlanner) },
            onEnableHourlySummary = { viewModel.setShowOverlayPermissionRationale(true) },
            onTestSummary = {
                 val intent = Intent(context, com.example.aitasklist.scheduler.HourlySummaryOverlayService::class.java)
                 if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                     context.startForegroundService(intent)
                 } else {
                     context.startService(intent)
                 }
            }
        )
        
        // ... (Dialogs: Calendar, Edit)
        if (showCalendarDialog) {
            CalendarSelectionDialog(
                calendars = uiState.calendars,
                defaultCalendarId = uiState.defaultCalendarId,
                onCalendarSelected = { viewModel.setDefaultCalendar(it) },
                onDismiss = { showCalendarDialog = false }
            )
        }

        if (showFilterDialog) {
            CalendarFilterDialog(
                calendars = uiState.allCalendars,
                excludedCalendarIds = uiState.excludedCalendarIds,
                onToggleExclusion = { id, excluded -> viewModel.toggleCalendarExclusion(id, excluded) },
                onToggleGroupExclusion = { ids, excluded -> viewModel.setCalendarExclusionBatch(ids, excluded) },
                onDismiss = { showFilterDialog = false }
            )
        }
        
        if (showRestoreDialog) {
            RestoreTasksDialog(
                deletedTasks = uiState.deletedTasks,
                onRestoreTask = { viewModel.restoreTask(it) },
                onHardDeleteTask = { viewModel.hardDeleteTask(it) },
                onDismiss = { showRestoreDialog = false }
            )
        }
        
        if (showEditDialog && editingTask != null) {
            EditTaskDialog(
                task = editingTask!!,
                onSave = { newContent ->
                    viewModel.updateTaskContent(editingTask!!.id, newContent)
                    showEditDialog = false
                },
                onDismiss = { showEditDialog = false }
            )
        }

        TaskInputSection(
            textInput = textInput,
            onTextInputChange = { textInput = it },
            isLoading = uiState.isLoading,
            onGenerateTasks = { input, split -> 
                viewModel.generateTasks(input, split)
                textInput = "" // Clear input on generate
            },
            onRemoveCompleted = { viewModel.removeCompletedTasks() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (uiState.error != null) {
            Text(
                text = stringResource(R.string.error_prefix, uiState.error ?: ""),
                color = MaterialTheme.colorScheme.error
            )
        } else {
            val sortOption = uiState.sortOption
            
            if (sortOption == SortOption.CUSTOM) {
                 // ... (Custom Sort Logic Identical to before)
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
                            val dragModifier = Modifier.pointerInput(Unit) {
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
                                                Collections.swap(currentList, currentIndex, currentIndex + 1)
                                                localTasks = currentList
                                                draggedIndex = currentIndex + 1 
                                                dragOffset = 0f 
                                            }
                                        } else if (dragOffset < -threshold) {
                                            if (currentIndex > 0) {
                                                val currentList = localTasks.toMutableList()
                                                Collections.swap(currentList, currentIndex, currentIndex - 1)
                                                localTasks = currentList
                                                draggedIndex = currentIndex - 1
                                                dragOffset = 0f
                                            }
                                        }
                                    },
                                    onDragEnd = { draggedIndex = null; dragOffset = 0f },
                                    onDragCancel = { draggedIndex = null; dragOffset = 0f }
                                )
                            }
                            bindTaskItem(task, true, dragModifier)
                         }
                     }
                 }
            } else if (isDailyPlanner) {
                DailyPlannerList(
                    tasks = uiState.tasks,
                    bindTaskItem = bindTaskItem
                )
            } else {
                // Default View: Scheduled vs Tasks
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val scheduledTasks = uiState.tasks.filter { it.scheduledDate != 0L || it.reminderTime != null }
                    val otherTasks = uiState.tasks.filter { it.scheduledDate == 0L && it.reminderTime == null }

                    if (scheduledTasks.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(
                                    text = "Scheduled",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            }
                        }
                        items(scheduledTasks, key = { it.id }) { task ->
                            bindTaskItem(task, false, null)
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
                        bindTaskItem(task, false, null)
                    }
                }
            }
        }
    }

    // Hourly Summary Sheet Integration
    var showHourlySheet by remember { mutableStateOf(false) }

    // Permission Rationale Dialog
    val showPermissionRationale by viewModel.showOverlayPermissionRationale.collectAsState()
    
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowOverlayPermissionRationale(false) },
            title = { Text("Permission Required") },
            text = { 
                Text("To display the Hourly Summary over other apps, AG Task List needs the 'Display over other apps' permission.") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setShowOverlayPermissionRationale(false)
                        val settingsIntent = android.content.Intent(
                             android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                             android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(settingsIntent)
                    }
                ) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowOverlayPermissionRationale(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Check Intent for Notification Trigger - Handled by MainActivity logic now triggering VM state
    LaunchedEffect(Unit) {
        // ... (Cleanup old logic if needed)
    }

    if (showHourlySheet) {
        val briefing = viewModel.getHourlyBriefing(System.currentTimeMillis())
        HourlySummarySheet(
            overdueTasks = briefing.overdueTasks,
            nextHourTasks = briefing.nextHourTasks,
            restOfDayTasks = briefing.restOfDayTasks,
            unscheduledTasks = briefing.unscheduledTasks,
            onDismiss = { showHourlySheet = false },
            onTaskClick = { /* Optional: Scrolls to task? */ }
        )
    }
}
