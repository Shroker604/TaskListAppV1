package com.example.aitasklist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.example.aitasklist.model.Task
import com.example.aitasklist.data.repository.CalendarInfo
import com.example.aitasklist.di.ServiceLocator
import com.example.aitasklist.domain.SortOption
import com.example.aitasklist.domain.HourlyBriefing
import com.example.aitasklist.util.DateUtils

data class TaskUiState(
    val tasks: List<Task> = emptyList(),
    val deletedTasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val calendars: List<CalendarInfo> = emptyList(),
    val allCalendars: List<CalendarInfo> = emptyList(),
    val defaultCalendarId: Long? = null,
    val sortOption: SortOption = SortOption.DATE_REMINDER,
    val sortAscending: Boolean = false,
    val userMessage: String? = null,
    val excludedCalendarIds: Set<String> = emptySet()
)

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    
    // Dependencies from Service Locator
    private val reminderManager = ServiceLocator.reminderManager!!
    private val repository = ServiceLocator.geminiRepository!!
    private val calendarRepository = ServiceLocator.calendarRepository!!
    private val taskDao = ServiceLocator.provideTaskDao(application)
    private val preferencesRepository = ServiceLocator.userPreferencesRepository!!
    private val syncManager = ServiceLocator.syncManager!!
    private val briefingManager = ServiceLocator.briefingManager!!
    private val taskScheduleManager = ServiceLocator.taskScheduleManager!!
    private val sortStrategyRegistry = ServiceLocator.sortStrategyRegistry
    
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    private val _allCalendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    private val _defaultCalendarId = MutableStateFlow<Long?>(null)
    private val _sortOption = MutableStateFlow(SortOption.DATE_REMINDER)
    private val _sortAscending = MutableStateFlow(false)

    // Permission Rationale State
    private val _showOverlayPermissionRationale = MutableStateFlow(false)
    val showOverlayPermissionRationale: StateFlow<Boolean> = _showOverlayPermissionRationale.asStateFlow()

    fun setShowOverlayPermissionRationale(show: Boolean) {
        _showOverlayPermissionRationale.value = show
    }

    init {
        loadCalendars()
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
        val strategy = sortStrategyRegistry.getStrategy(state.sortOption)
        val sortedTasks = strategy.sort(state.tasks, sortAscending)
        state.copy(tasks = sortedTasks, sortAscending = sortAscending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskUiState())

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun setSortOption(option: SortOption) {
        if (_sortOption.value == option) {
            _sortAscending.value = !_sortAscending.value
        } else {
            _sortOption.value = option
            _sortAscending.value = false
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
                // Now using TaskGeneratorRepository definition (interface)
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
                if (!it.isCompleted) { 
                    reminderManager.cancelReminder(it.id)
                }
            }
        }
    }

    fun updateTaskSchedule(taskId: String, newDate: Long, newTime: Long?) {
        viewModelScope.launch {
            val task = uiState.value.tasks.find { it.id == taskId }
            task?.let {
                taskScheduleManager.updateTaskSchedule(it, newDate, newTime)
            }
        }
    }

    fun updateTaskDate(taskId: String, newDate: Long) {
        viewModelScope.launch {
            val task = uiState.value.tasks.find { it.id == taskId }
            task?.let {
                taskScheduleManager.updateTaskDate(it, newDate)
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
                taskScheduleManager.updateTaskSchedule(it, it.scheduledDate, time)
            }
        }
    }

    fun updateTaskPriority(taskId: String, priority: com.example.aitasklist.model.Priority) {
        viewModelScope.launch {
            val task = uiState.value.tasks.find { it.id == taskId }
            task?.let {
                taskDao.updateTask(it.copy(priority = priority))
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
                    taskDao.updateTask(task.copy(isDeleted = true))
                } else {
                    taskDao.deleteTask(task)
                }
                reminderManager.cancelReminder(task.id)
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val task = uiState.value.tasks.find { it.id == taskId }
            task?.let {
                if (it.calendarEventId != null) {
                    taskDao.updateTask(it.copy(isDeleted = true))
                } else {
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
                    taskDao.updateTask(task.copy(isDeleted = true))
                } else {
                    taskDao.deleteTask(task)
                }
                reminderManager.cancelReminder(task.id)
            }
        }
    }

    fun addToCalendar(task: Task, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val result = taskScheduleManager.addToCalendar(task)
            result?.let { (_, accountName) ->
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
                val now = java.util.Calendar.getInstance()
                val deadlineHour = preferencesRepository.autoScheduleDeadlineHour.first()
                val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
                
                var isRollover = false
                val startOfWindow = if (currentHour >= deadlineHour) {
                    isRollover = true
                    java.util.Calendar.getInstance().apply {
                        add(java.util.Calendar.DAY_OF_YEAR, 1)
                        set(java.util.Calendar.HOUR_OF_DAY, 8)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                } else {
                    now
                }
                
                val endOfWindow = java.util.Calendar.getInstance().apply {
                    timeInMillis = startOfWindow.timeInMillis 
                    if (!isRollover) add(java.util.Calendar.DAY_OF_YEAR, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 23)
                    set(java.util.Calendar.MINUTE, 59)
                }

                val excludedIds = excludedCalendarIds.value
                val allTasks = uiState.value.tasks // Current State tasks
                
                // DELEGATE TO SYNC MANAGER
                val result = syncManager.performFullSync(
                    windowStart = startOfWindow.timeInMillis,
                    windowEnd = endOfWindow.timeInMillis,
                    excludedCalendarIds = excludedIds,
                    allTasks = allTasks
                )
                
                val deadlineMsg = if (isRollover) "(Too late for today, scheduled for Tomorrow)" else ""
                _userMessage.value = "Events: ${result.eventsCount}, Gaps: ${result.gapsCount}. Scheduled: ${result.scheduledCount}. $deadlineMsg"

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
                    taskDao.updateTask(task.copy(calendarEventId = null))
                    onError()
                }
            } else {
                onError()
            }
        }
    }

    fun getHourlyBriefing(now: Long): HourlyBriefing {
        // DELEGATE TO BRIEFING MANAGER
        return briefingManager.getBriefingForTasks(now, uiState.value.tasks)
    }
}
