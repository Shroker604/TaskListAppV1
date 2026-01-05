package com.example.aitasklist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.aitasklist.model.Task
import com.example.aitasklist.data.remote.GeminiRepository
import com.example.aitasklist.data.repository.CalendarRepository
import com.example.aitasklist.data.repository.CalendarInfo
import com.example.aitasklist.data.UserPreferencesRepository

enum class SortOption {
    CREATION_DATE, // Default: Newest first
    DATE_REMINDER,  // Scheduled Date -> Reminder Time
    CUSTOM         // User defined order
}

data class TaskUiState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val calendars: List<CalendarInfo> = emptyList(), // Writable for Default
    val allCalendars: List<CalendarInfo> = emptyList(), // All for Filtering
    val defaultCalendarId: Long? = null,
    val sortOption: SortOption = SortOption.DATE_REMINDER,
    val sortAscending: Boolean = false, // Default: Descending (Newest First)
    val userMessage: String? = null,
    val excludedCalendarIds: Set<String> = emptySet()
)

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val reminderManager = com.example.aitasklist.scheduler.ReminderManager(application)
    private val repository = GeminiRepository()
    private val calendarRepository = CalendarRepository(application)
    private val taskDao = (application as TaskApplication).database.taskDao()
    private val preferencesRepository = UserPreferencesRepository(application)
    private val calendarGapManager = com.example.aitasklist.scheduler.CalendarGapManager()
    private val autoScheduler = com.example.aitasklist.scheduler.AutoScheduler()
    
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _userMessage = MutableStateFlow<String?>(null) // Success messages
    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    private val _allCalendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    private val _defaultCalendarId = MutableStateFlow<Long?>(null)
    private val _sortOption = MutableStateFlow(SortOption.DATE_REMINDER)
    private val _sortAscending = MutableStateFlow(false)

    val isDarkTheme = preferencesRepository.isDarkTheme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val isDailyPlanner = preferencesRepository.isDailyPlanner.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val excludedCalendarIds = preferencesRepository.excludedCalendarIds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )
    
    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDarkTheme(enabled)
        }
    }

    fun setDailyPlanner(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDailyPlanner(enabled)
        }
    }

    val uiState: StateFlow<TaskUiState> = combine(
        taskDao.getAllTasks(),
        _isLoading,
        _error,
        _calendars,
        _allCalendars
    ) { tasks, isLoading, error, calendars, allCalendars ->
        TaskUiState(
            tasks = tasks, 
            isLoading = isLoading, 
            error = error, 
            calendars = calendars, 
            allCalendars = allCalendars
        )
    }.combine(_defaultCalendarId) { state, defaultId ->
        state.copy(defaultCalendarId = defaultId)
    }.combine(_userMessage) { state, userMessage ->
        state.copy(userMessage = userMessage)
    }.combine(_sortOption) { state, sortOption -> state.copy(sortOption = sortOption) 
    }.combine(excludedCalendarIds) { state, excluded -> state.copy(excludedCalendarIds = excluded) }
    .combine(_sortAscending) { state, sortAscending ->
        val sortedTasks = when (state.sortOption) {
            SortOption.CREATION_DATE -> {
                if (sortAscending) {
                    state.tasks.sortedBy { it.createdAt }
                } else {
                    state.tasks.sortedByDescending { it.createdAt }
                }
            }
            SortOption.DATE_REMINDER -> {
                val comparator = Comparator<Task> { t1, t2 ->
                    // 1. Compare Dates (Start of Day)
                    // Treat 0L as "No Date", effectively infinite future or separate category
                    val date1 = if (t1.scheduledDate == 0L) Long.MAX_VALUE else getStartOfDay(t1.scheduledDate)
                    val date2 = if (t2.scheduledDate == 0L) Long.MAX_VALUE else getStartOfDay(t2.scheduledDate)
                    
                    val dateResult = if (sortAscending) {
                        date1.compareTo(date2)
                    } else {
                        date2.compareTo(date1)
                    }

                    if (dateResult != 0) return@Comparator dateResult

                    // 2. Priority: Task with reminder comes first
                    val hasReminder1 = t1.reminderTime != null
                    val hasReminder2 = t2.reminderTime != null
                    
                    if (hasReminder1 && !hasReminder2) return@Comparator -1
                    if (!hasReminder1 && hasReminder2) return@Comparator 1
                    
                    // 3. Compare Reminder Time
                    if (hasReminder1 && hasReminder2) {
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
                state.tasks.sortedWith(comparator)
            }
            SortOption.CUSTOM -> {
                state.tasks.sortedBy { it.orderIndex }
            }
        }
        state.copy(tasks = sortedTasks, sortAscending = sortAscending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskUiState())

    fun clearUserMessage() {
        _userMessage.value = null
    }

    // ... (rest of methods)

    fun setSortOption(option: SortOption) {
        if (_sortOption.value == option) {
            _sortAscending.value = !_sortAscending.value
        } else {
            _sortOption.value = option
            _sortAscending.value = false // Default to descending when switching
        }
    }

    private fun getStartOfDay(millis: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = millis
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun updateAllTasksOrder(tasks: List<Task>) {
        viewModelScope.launch {
             val updatedTasks = tasks.mapIndexed { index, task ->
                task.copy(orderIndex = index)
            }
            taskDao.updateTasks(updatedTasks)
        }
    }




    fun loadCalendars() {
        viewModelScope.launch {
            _calendars.value = calendarRepository.getWritableCalendars()
            _allCalendars.value = calendarRepository.getAllCalendars()
            _defaultCalendarId.value = calendarRepository.getDefaultCalendarId()
        }
    }

    fun setDefaultCalendar(id: Long) {
        viewModelScope.launch {
            calendarRepository.saveDefaultCalendarId(id)
            _defaultCalendarId.value = id
        }
    }

    fun toggleCalendarExclusion(calendarId: Long, excluded: Boolean) {
        viewModelScope.launch {
            val current = excludedCalendarIds.value.toMutableSet()
            if (excluded) {
                current.add(calendarId.toString())
            } else {
                current.remove(calendarId.toString())
            }
            preferencesRepository.setExcludedCalendarIds(current)
        }
    }

    fun setCalendarExclusionBatch(calendarIds: List<Long>, excluded: Boolean) {
        viewModelScope.launch {
            val current = excludedCalendarIds.value.toMutableSet()
            if (excluded) {
                current.addAll(calendarIds.map { it.toString() })
            } else {
                current.removeAll(calendarIds.map { it.toString() })
            }
            preferencesRepository.setExcludedCalendarIds(current)
        }
    }

    fun generateTasks(input: String, splitTasks: Boolean) {
        if (input.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val taskStrings = repository.parseTasks(input, splitTasks)
                val newTasks = taskStrings.map { Task(content = it) }
                taskDao.insertTasks(newTasks)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun toggleTaskCompletion(taskId: String) {
        viewModelScope.launch {
            val task = uiState.value.tasks.find { it.id == taskId }
            task?.let {
                taskDao.updateTask(it.copy(isCompleted = !it.isCompleted))
                // Cancel reminder if completed
                if (!it.isCompleted) { 
                    reminderManager.cancelReminder(it.id)
                }
            }
        }
    }

    fun updateTaskDate(taskId: String, newDate: Long) {
        viewModelScope.launch {
            val task = uiState.value.tasks.find { it.id == taskId }
            task?.let {
                taskDao.updateTask(it.copy(scheduledDate = newDate))
                
                if (it.calendarEventId != null) {
                    calendarRepository.updateCalendarEvent(
                        eventId = it.calendarEventId,
                        title = it.content,
                        description = "Created from AI Task List",
                        startTime = newDate,
                        endTime = newDate + 3600000 // 1 hour
                    )
                }
            }
        }
    }
    
    fun updateTaskContent(taskId: String, newContent: String) {
        viewModelScope.launch {
            val task = uiState.value.tasks.find { it.id == taskId }
            task?.let {
                taskDao.updateTask(it.copy(content = newContent))
            }
        }
    }

    fun updateTaskReminder(taskId: String, time: Long) {
        viewModelScope.launch {
            val task = uiState.value.tasks.find { it.id == taskId }
            task?.let {
                taskDao.updateTask(it.copy(reminderTime = time))
                reminderManager.scheduleReminder(it.id, it.content, time, it.priority.name)
            }
        }
    }

    fun updateTaskPriority(taskId: String, priority: com.example.aitasklist.model.Priority) {
        viewModelScope.launch {
            val task = uiState.value.tasks.find { it.id == taskId }
            task?.let {
                taskDao.updateTask(it.copy(priority = priority))
                // Reschedule if reminder exists to update channel/type
                if (it.reminderTime != null) {
                    reminderManager.scheduleReminder(it.id, it.content, it.reminderTime, priority.name)
                }
            }
        }
    }

    fun removeCompletedTasks() {
        viewModelScope.launch {
            val completedTasks = uiState.value.tasks.filter { it.isCompleted }
            completedTasks.forEach { task ->
                if (task.calendarEventId != null) {
                    calendarRepository.deleteCalendarEvent(task.calendarEventId)
                }
                reminderManager.cancelReminder(task.id)
            }
            taskDao.deleteCompletedTasks()
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val task = uiState.value.tasks.find { it.id == taskId }
            task?.let {
                if (it.calendarEventId != null) {
                    calendarRepository.deleteCalendarEvent(it.calendarEventId)
                }
                reminderManager.cancelReminder(it.id)
                taskDao.deleteTask(it)
            }
        }
    }

    fun clearTasks() {
        viewModelScope.launch {
            val allTasks = uiState.value.tasks
            allTasks.forEach { task ->
                if (task.calendarEventId != null) {
                    calendarRepository.deleteCalendarEvent(task.calendarEventId)
                }
                reminderManager.cancelReminder(task.id)
            }
            taskDao.deleteAllTasks()
        }
    }

    fun addToCalendar(task: Task, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            // Default duration 1 hour if not specified
            val startTime = task.scheduledDate
            val endTime = startTime + 3600000 // 1 hour
            val calendarId = _defaultCalendarId.value
            
            val result = calendarRepository.addToCalendar(
                title = task.content,
                description = "Created from AI Task List",
                startTime = startTime,
                endTime = endTime,
                calendarId = calendarId
            )
            
            if (result != null) {
                val (eventId, accountName) = result
                taskDao.updateTask(task.copy(calendarEventId = eventId))
                onSuccess(accountName)
            }
        }
    }

    fun autoScheduleToday() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Define Window: Now -> End of Tomorrow (User Feedback: "Multiple items for tomorrow")
                val now = java.util.Calendar.getInstance()
                val endOfWindow = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 1) // Tomorrow
                    set(java.util.Calendar.HOUR_OF_DAY, 23)
                    set(java.util.Calendar.MINUTE, 59)
                }

                // 2. Fetch ALL Calendar Events in Window (Today & Tomorrow)
                // This is the source of truth for "Busy" slots
                // Filter by Excluded Calendars
                val excludedIds = excludedCalendarIds.value
                var events = calendarRepository.getEventsInRange(now.timeInMillis, endOfWindow.timeInMillis, excludedIds)
                
                var importedCount = 0
                var manualPushedCount = 0

                // 3. PULL SYNC: Import new events from Calendar
                // Run this ALWAYS to ensure we see "busy status" items user mentioned
                events.forEach { event ->
                    var existingTask = try {
                        taskDao.getTaskByCalendarEventId(event.id.toString())
                    } catch (e: Exception) { null }

                    // DEDUPLICATION: If not found by ID, try Fuzzy Match (Title + Same Day)
                    if (existingTask == null) {
                        val startOfDay = getStartOfDay(event.startTime)
                        val endOfDay = startOfDay + (24 * 60 * 60 * 1000) - 1
                        existingTask = try {
                            taskDao.findTaskByTitleAndDate(event.title, startOfDay, endOfDay)
                        } catch (e: Exception) { null }

                        // FLOATING MATCH: If still not found, try finding an UNSCHEDULED task with same Title
                        if (existingTask == null) {
                            existingTask = try {
                                taskDao.findUnscheduledTaskByTitle(event.title)
                            } catch (e: Exception) { null }
                        }

                        if (existingTask != null) {
                            // Match Found (Fuzzy Day or Floating)! Link them.
                             // Update local tasks to match GC time (User Request: "Let that be the google calendar time")
                            val updatedTask = existingTask.copy(
                                calendarEventId = event.id,
                                scheduledDate = event.startTime,
                                reminderTime = if (event.isAllDay) null else event.startTime // Sync Reminder to Start Time (Null if AllDay)
                            )
                            taskDao.updateTask(updatedTask)
                            
                            // Reschedule local reminder
                            if (updatedTask.reminderTime != null) {
                                reminderManager.scheduleReminder(updatedTask.id, updatedTask.content, updatedTask.reminderTime, updatedTask.priority.name)
                            }
                        }
                    } else {
                        // Task exists by ID. Update time to match GC if different? 
                        // User request implies GC is source of truth for time on sync.
                        if (existingTask.scheduledDate != event.startTime || existingTask.reminderTime != event.startTime) {
                             val updatedTask = existingTask.copy(
                                scheduledDate = event.startTime,
                                reminderTime = if (event.isAllDay) null else event.startTime
                            )
                            taskDao.updateTask(updatedTask)
                            if (updatedTask.reminderTime != null) {
                                reminderManager.scheduleReminder(updatedTask.id, updatedTask.content, updatedTask.reminderTime, updatedTask.priority.name)
                            }
                        }
                    }

                    if (existingTask == null) {
                        val newTask = Task(
                            content = event.title,
                            scheduledDate = event.startTime,
                            calendarEventId = event.id,
                            priority = com.example.aitasklist.model.Priority.MEDIUM,
                            isCompleted = false,
                            reminderTime = if (event.isAllDay) null else event.startTime // New Task gets Reminder at Event Start (Null if AllDay)
                        )
                        taskDao.insertTasks(listOf(newTask))
                        
                        // Schedule notification
                        if (newTask.reminderTime != null) {
                            reminderManager.scheduleReminder(newTask.id, newTask.content, newTask.reminderTime, newTask.priority.name)
                        }
                        
                        importedCount++
                    }
                }

                // 4. PUSH SYNC: Identify Manual Local Changes
                // Tasks with a Date set (in window) but NO Calendar ID.
                // These are "tasks that have a date for today that doesn't seem to appear in google calendar"
                val manualTasksToSync = uiState.value.tasks.filter { 
                    !it.isCompleted && 
                    it.scheduledDate >= now.timeInMillis && 
                    it.scheduledDate <= endOfWindow.timeInMillis &&
                    it.calendarEventId == null
                }

                if (manualTasksToSync.isNotEmpty()) {
                    val updatedManualTasks = mutableListOf<Task>()
                    val defaultDuration = com.example.aitasklist.scheduler.AutoScheduler.DEFAULT_TASK_DURATION_MS
                    
                    manualTasksToSync.forEach { task ->
                        val endTime = task.scheduledDate + defaultDuration
                        val result = calendarRepository.addToCalendar(
                            title = task.content,
                            description = "Synced from AI Task List",
                            startTime = task.scheduledDate,
                            endTime = endTime,
                            calendarId = _defaultCalendarId.value,
                            isAllDay = task.reminderTime == null // All Day if No Reminder Time
                        )
                        if (result != null) {
                            manualPushedCount++
                            updatedManualTasks.add(task.copy(calendarEventId = result.first))
                        }
                    }
                    if (updatedManualTasks.isNotEmpty()) {
                        taskDao.updateTasks(updatedManualTasks)
                    }
                    
                    // RE-FETCH Events because we just added some, and we need accurate gaps for Auto-Schedule
                    events = calendarRepository.getEventsInRange(now.timeInMillis, endOfWindow.timeInMillis, excludedIds)
                }

                // 5. AUTO-SCHEDULE: Fill remaining gaps with unscheduled tasks
                val busySlots = events.map { 
                    com.example.aitasklist.scheduler.TimeSlot(it.startTime, it.endTime) 
                }
                val gaps = calendarGapManager.findGaps(now.timeInMillis, endOfWindow.timeInMillis, busySlots)

                val unscheduledTasks = uiState.value.tasks.filter { 
                    !it.isCompleted && it.scheduledDate == 0L && it.reminderTime == null 
                }
                
                // Normalize "Today" for scorer, but gaps cover tomorrow too
                val todayNormalized = getStartOfDay(now.timeInMillis)
                val scheduledTasks = autoScheduler.scheduleTasks(unscheduledTasks, gaps, todayNormalized)
                var autoScheduledCount = 0

                // 6. Update DB & Sync Auto-Scheduled Tasks
                if (scheduledTasks.isNotEmpty()) {
                    val finalTasks = mutableListOf<Task>()
                    scheduledTasks.forEach { task ->
                        val duration = com.example.aitasklist.scheduler.AutoScheduler.DEFAULT_TASK_DURATION_MS
                        val startTime = task.scheduledDate 
                        val endTime = startTime + duration
                        val result = calendarRepository.addToCalendar(
                            title = task.content,
                            description = "Auto-Scheduled by AI Task List",
                            startTime = startTime,
                            endTime = endTime,
                            calendarId = _defaultCalendarId.value,
                            isAllDay = false // Auto-Scheduled always has time
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
                    autoScheduledCount = finalTasks.size
                }

                // 7. Feedback Construction
                val parts = mutableListOf<String>()
                if (importedCount > 0) parts.add("Imported $importedCount")
                if (manualPushedCount > 0) parts.add("Synced $manualPushedCount local")
                if (autoScheduledCount > 0) parts.add("Auto-Scheduled $autoScheduledCount")
                
                if (parts.isNotEmpty()) {
                    _userMessage.value = "Sync Complete: ${parts.joinToString(", ")}"
                } else {
                    _userMessage.value = "Sync Complete: No changes needed."
                }

            } catch (e: Exception) {
                 _error.value = "Sync Failed: ${e.message}"
                 e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun openCalendarEvent(task: Task, onSuccess: (android.net.Uri) -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            if (task.calendarEventId != null) {
                val uri = calendarRepository.getEventUri(task.calendarEventId)
                if (uri != null) {
                    onSuccess(uri)
                } else {
                    // Event not found, clear ID
                    taskDao.updateTask(task.copy(calendarEventId = null))
                    onError()
                }
            } else {
                onError()
            }
        }
    }
}
