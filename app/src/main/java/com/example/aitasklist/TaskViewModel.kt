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

enum class SortOption {
    CREATION_DATE, // Default: Newest first
    DATE_REMINDER,  // Scheduled Date -> Reminder Time
    CUSTOM         // User defined order
}

data class TaskUiState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val calendars: List<CalendarInfo> = emptyList(),
    val defaultCalendarId: Long? = null,
    val sortOption: SortOption = SortOption.DATE_REMINDER,
    val sortAscending: Boolean = false // Default: Descending (Newest First)
)

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val reminderManager = com.example.aitasklist.scheduler.ReminderManager(application)
    private val repository = GeminiRepository()
    private val calendarRepository = CalendarRepository(application)
    private val taskDao = (application as TaskApplication).database.taskDao()
    
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    private val _defaultCalendarId = MutableStateFlow<Long?>(null)
    private val _sortOption = MutableStateFlow(SortOption.DATE_REMINDER)
    private val _sortAscending = MutableStateFlow(false)

    val uiState: StateFlow<TaskUiState> = combine(
        taskDao.getAllTasks(),
        _isLoading,
        _error,
        _calendars,
        _defaultCalendarId
    ) { tasks, isLoading, error, calendars, defaultId ->
        TaskUiState(tasks = tasks, isLoading = isLoading, error = error, calendars = calendars, defaultCalendarId = defaultId)
    }.combine(_sortOption) { state, sortOption -> state.copy(sortOption = sortOption) }
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
                    
                    // 3. Compare Reminder Time (Always Ascending: Earliest first)
                    if (hasReminder1 && hasReminder2) {
                        // Both have reminders, compare time
                        t1.reminderTime!!.compareTo(t2.reminderTime!!)
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
            _defaultCalendarId.value = calendarRepository.getDefaultCalendarId()
        }
    }

    fun setDefaultCalendar(id: Long) {
        viewModelScope.launch {
            calendarRepository.saveDefaultCalendarId(id)
            _defaultCalendarId.value = id
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

    fun updateTaskReminder(taskId: String, time: Long) {
        viewModelScope.launch {
            val task = uiState.value.tasks.find { it.id == taskId }
            task?.let {
                taskDao.updateTask(it.copy(reminderTime = time))
                reminderManager.scheduleReminder(it.id, it.content, time)
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
