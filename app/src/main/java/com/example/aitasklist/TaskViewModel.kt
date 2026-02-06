package com.example.aitasklist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.example.aitasklist.model.Task
import com.example.aitasklist.data.remote.GeminiRepository
import com.example.aitasklist.data.repository.CalendarRepository
import com.example.aitasklist.data.repository.CalendarInfo
import com.example.aitasklist.data.UserPreferencesRepository

import com.example.aitasklist.domain.SortOption
import com.example.aitasklist.domain.SortStrategyRegistry
import com.example.aitasklist.util.DateUtils

data class TaskUiState(
    val tasks: List<Task> = emptyList(),
    val deletedTasks: List<Task> = emptyList(), // Added for Restore UI
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

    init {
        loadCalendars()
        // Auto-Prune old soft-deleted tasks (older than 30 days)
        pruneOldTasks()
    }

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
        taskDao.getDeletedTasks(),
        _isLoading,
        _error,
        _calendars
        // _allCalendars moved to next combine
    ) { tasks, deletedTasks, isLoading, error, calendars ->
        TaskUiState(
            tasks = tasks,
            deletedTasks = deletedTasks,
            isLoading = isLoading, 
            error = error, 
            calendars = calendars
        )
    }.combine(_allCalendars) { state, allCalendars ->
        state.copy(allCalendars = allCalendars)
    }.combine(_defaultCalendarId) { state, defaultId ->
        state.copy(defaultCalendarId = defaultId)
    }.combine(_userMessage) { state, userMessage ->
        state.copy(userMessage = userMessage)
    }.combine(_sortOption) { state, sortOption -> state.copy(sortOption = sortOption) 
    }.combine(excludedCalendarIds) { state, excluded -> state.copy(excludedCalendarIds = excluded) }
    .combine(_sortAscending) { state, sortAscending ->
        val strategy = SortStrategyRegistry().getStrategy(state.sortOption)
        val sortedTasks = strategy.sort(state.tasks, sortAscending)
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
                // If it's a synced task, Soft Delete it (Hide & Prevent Resync)
                if (task.calendarEventId != null) {
                    taskDao.updateTask(task.copy(isDeleted = true))
                    // Do NOT delete from Calendar (User request: "Prevent Resync", implies keeping on cal but ignoring locally)
                } else {
                    // Local task -> Hard Delete
                    taskDao.deleteTask(task)
                }
                reminderManager.cancelReminder(task.id)
            }
            // For batch local delete (if any remain that were hard deleted above, though doing one by one is safer for mixed list)
            // simplify: Filter local only for batch? Or just iterate.
            // Iteration above covers it.
            // taskDao.deleteCompletedTasks() // This would hard delete everything. We must avoid calling this if we soft deleting some.
            // Alternative: Call deleteCompletedTasks() ONLY for unsynced?
            // Let's rely on the loop above for safety.
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val task = uiState.value.tasks.find { it.id == taskId }
            task?.let {
                if (it.calendarEventId != null) {
                    // Synced Task -> Soft Delete
                    taskDao.updateTask(it.copy(isDeleted = true))
                } else {
                    // Local Task -> Hard Delete
                    if (it.calendarEventId != null) {
                        calendarRepository.deleteCalendarEvent(it.calendarEventId)
                    }
                    taskDao.deleteTask(it)
                }
                reminderManager.cancelReminder(it.id)
            }
        }
    }

    fun restoreTask(taskId: String) {
        viewModelScope.launch {
            val task = uiState.value.deletedTasks.find { it.id == taskId }
            task?.let {
                 taskDao.updateTask(it.copy(isDeleted = false))
            }
        }
    }

    fun hardDeleteTask(taskId: String) {
        viewModelScope.launch {
            val task = uiState.value.deletedTasks.find { it.id == taskId } ?: uiState.value.tasks.find { it.id == taskId }
            task?.let {
                taskDao.deleteTask(it)
            }
        }
    }

    fun clearTasks() {
        viewModelScope.launch {
            val allTasks = uiState.value.tasks
            allTasks.forEach { task ->
                if (task.calendarEventId != null) {
                    // Soft Delete
                    taskDao.updateTask(task.copy(isDeleted = true))
                } else {
                    // Hard Delete
                    taskDao.deleteTask(task)
                }
                reminderManager.cancelReminder(task.id)
            }
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

    private fun pruneOldTasks() {
        viewModelScope.launch {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            taskDao.deleteSoftDeletedOlderThan(thirtyDaysAgo)
        }
    }

    fun autoScheduleToday() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Define Window: Smart Rollover Check
                val now = java.util.Calendar.getInstance()
                
                // Get Deadline Preference
                val deadlineHour = preferencesRepository.autoScheduleDeadlineHour.first()
                val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
                
                var isRollover = false
                val startOfWindow = if (currentHour >= deadlineHour) {
                    isRollover = true
                    java.util.Calendar.getInstance().apply {
                        add(java.util.Calendar.DAY_OF_YEAR, 1) // Start Tomorrow
                        set(java.util.Calendar.HOUR_OF_DAY, 8) // Default Start: 8 AM
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                } else {
                    now // Start Now
                }
                
                val endOfWindow = java.util.Calendar.getInstance().apply {
                    timeInMillis = startOfWindow.timeInMillis // Base end relative to start frame
                    if (!isRollover) add(java.util.Calendar.DAY_OF_YEAR, 1) // If starting today, go to end of tomorrow (wide window)
                    // If rolling over to tomorrow, we stay in tomorrow (single day focus) or extend to next day?
                    // Let's keep the "Today + Tomorrow" wide window logic, but shifted.
                    // If rollover: Start Tomorrow -> End Tomorrow
                    
                    set(java.util.Calendar.HOUR_OF_DAY, 23)
                    set(java.util.Calendar.MINUTE, 59)
                }

                // 2. Fetch ALL Calendar Events in Window (Today & Tomorrow)
                // This is the source of truth for "Busy" slots
                // Filter by Excluded Calendars
                val excludedIds = excludedCalendarIds.value
                var events = calendarRepository.getEventsInRange(startOfWindow.timeInMillis, endOfWindow.timeInMillis, excludedIds)

                // 3. PULL SYNC: Import new events from Calendar
                val importedCount = performPullSync(events)

                // 4. PUSH SYNC: Identify Manual Local Changes
                val manualPushedCount = performPushSync(startOfWindow.timeInMillis, endOfWindow.timeInMillis)
                
                // Re-fetch events if we pushed new ones, to ensure accurate gaps
                if (manualPushedCount > 0) {
                     events = calendarRepository.getEventsInRange(startOfWindow.timeInMillis, endOfWindow.timeInMillis, excludedIds)
                }

                // 5. AUTO-SCHEDULE: Fill remaining gaps with unscheduled tasks
                val autoScheduledCount = performAutoSchedule(startOfWindow.timeInMillis, endOfWindow.timeInMillis, events)

                // 6. Feedback Construction
                val busyCount = events.size
                val busySlots = events.map { com.example.aitasklist.scheduler.TimeSlot(it.startTime, it.endTime) }
                val gaps = calendarGapManager.findGaps(startOfWindow.timeInMillis, endOfWindow.timeInMillis, busySlots)
                
                val deadlineMsg = if (isRollover) "(Too late for today, scheduled for Tomorrow)" else ""
                _userMessage.value = "Events: $busyCount, Gaps: ${gaps.size}. Scheduled: $autoScheduledCount. $deadlineMsg"

            } catch (e: Exception) {
                 _error.value = "Sync Failed: ${e.message}"
                 e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun performPullSync(events: List<com.example.aitasklist.data.repository.CalendarEvent>): Int {
        var count = 0
        events.forEach { event ->
            val existingTask = findMatchingTask(event)
            
            // If Soft Deleted, SKIP (Prevent Resync)
            if (existingTask != null && existingTask.isDeleted) {
                return@forEach
            }

            if (existingTask != null) {
                // Check if update needed
                if (shouldUpdateTask(existingTask, event)) {
                    updateTaskFromEvent(existingTask, event)
                }
            } else {
                createNewTaskFromEvent(event)
                count++
            }
        }
        return count
    }

    private suspend fun findMatchingTask(event: com.example.aitasklist.data.repository.CalendarEvent): Task? {
        // 1. Try ID Match
        try {
            val task = taskDao.getTaskByCalendarEventIdIncludingDeleted(event.id)
            if (task != null) return task
        } catch (e: Exception) { /* ignore */ }

        // 2. Fuzzy Match: Same Title + Same Day
        val startOfDay = DateUtils.getStartOfDay(event.startTime)
        val endOfDay = startOfDay + (24 * 60 * 60 * 1000) - 1
        try {
            val task = taskDao.findTaskByTitleAndDate(event.title, startOfDay, endOfDay)
            if (task != null) return task
        } catch (e: Exception) { /* ignore */ }

        // 3. Unscheduled Match: Same Title + No Date
        try {
            val task = taskDao.findUnscheduledTaskByTitle(event.title)
            if (task != null) return task
        } catch (e: Exception) { /* ignore */ }

        return null
    }

    private fun shouldUpdateTask(task: Task, event: com.example.aitasklist.data.repository.CalendarEvent): Boolean {
        // If not linked yet, we should update (link it)
        if (task.calendarEventId != event.id) return true
        
        // If linked, check for changes
        // Note: We sync reminder time if it differs from event start (unless all day)
        val expectedReminderData = if (event.isAllDay) null else event.startTime
        
        return task.scheduledDate != event.startTime ||
               task.reminderTime != expectedReminderData ||
               task.isRecurring != event.isRecurring
    }

    private suspend fun updateTaskFromEvent(task: Task, event: com.example.aitasklist.data.repository.CalendarEvent) {
        val updatedTask = task.copy(
            calendarEventId = event.id,
            scheduledDate = event.startTime,
            reminderTime = if (event.isAllDay) null else event.startTime,
            isRecurring = event.isRecurring
        )
        taskDao.updateTask(updatedTask)
        updatedTask.reminderTime?.let {
            reminderManager.scheduleReminder(updatedTask.id, updatedTask.content, it, updatedTask.priority.name)
        }
    }

    private suspend fun createNewTaskFromEvent(event: com.example.aitasklist.data.repository.CalendarEvent) {
        val newTask = Task(
            content = event.title,
            scheduledDate = event.startTime,
            calendarEventId = event.id,
            priority = com.example.aitasklist.model.Priority.MEDIUM,
            isCompleted = false,
            reminderTime = if (event.isAllDay) null else event.startTime,
            isRecurring = event.isRecurring
        )
        taskDao.insertTasks(listOf(newTask))
        newTask.reminderTime?.let {
            reminderManager.scheduleReminder(newTask.id, newTask.content, it, newTask.priority.name)
        }
    }

    private suspend fun performPushSync(startTime: Long, endTime: Long): Int {
        val tasksToSync = uiState.value.tasks.filter { 
            !it.isCompleted && 
            it.scheduledDate >= startTime && 
            it.scheduledDate <= endTime &&
            it.calendarEventId == null
        }

        if (tasksToSync.isEmpty()) return 0

        var pushedCount = 0
        val updatedTasks = mutableListOf<Task>()
        val defaultDuration = com.example.aitasklist.scheduler.AutoScheduler.DEFAULT_TASK_DURATION_MS
        
        tasksToSync.forEach { task ->
            val endParams = task.scheduledDate + defaultDuration
            val result = calendarRepository.addToCalendar(
                title = task.content,
                description = "Synced from AI Task List",
                startTime = task.scheduledDate,
                endTime = endParams,
                calendarId = _defaultCalendarId.value,
                isAllDay = task.reminderTime == null // All Day if No Reminder Time
            )
            if (result != null) {
                pushedCount++
                updatedTasks.add(task.copy(calendarEventId = result.first))
            }
        }
        if (updatedTasks.isNotEmpty()) {
            taskDao.updateTasks(updatedTasks)
        }
        return pushedCount
    }

    private suspend fun performAutoSchedule(windowStart: Long, windowEnd: Long, events: List<com.example.aitasklist.data.repository.CalendarEvent>): Int {
        val busySlots = events.map { 
            com.example.aitasklist.scheduler.TimeSlot(it.startTime, it.endTime) 
        }
        val gaps = calendarGapManager.findGaps(windowStart, windowEnd, busySlots)

        val unscheduledTasks = uiState.value.tasks.filter { 
            !it.isCompleted && it.scheduledDate == 0L && it.reminderTime == null 
        }
        
        val todayNormalized = DateUtils.getStartOfDay(windowStart)
        val scheduledTasks = autoScheduler.scheduleTasks(unscheduledTasks, gaps, todayNormalized)
        
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
            return finalTasks.size
        }
        return 0
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

    fun getHourlyBriefing(now: Long): HourlyBriefing {
        val allTasks = uiState.value.tasks.filter { !it.isCompleted && !it.isDeleted }
        val oneHourLater = now + 3600000
        val endOfDay = DateUtils.getEndOfDay(now)

        val overdue = allTasks.filter {
            it.scheduledDate > 0L && it.scheduledDate < now
        }.sortedBy { it.scheduledDate }

        val nextHour = allTasks.filter { 
            it.scheduledDate in now..oneHourLater 
        }.sortedBy { it.scheduledDate }

        val restOfDay = allTasks.filter { 
            it.scheduledDate > oneHourLater && it.scheduledDate <= endOfDay
        }.sortedBy { it.scheduledDate }

        val unscheduled = allTasks.filter { 
            it.scheduledDate == 0L 
        }

        return HourlyBriefing(overdue, nextHour, restOfDay, unscheduled)
    }
}

data class HourlyBriefing(
    val overdueTasks: List<Task>,
    val nextHourTasks: List<Task>,
    val restOfDayTasks: List<Task>,
    val unscheduledTasks: List<Task>
)
