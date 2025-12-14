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

data class TaskUiState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val calendars: List<CalendarInfo> = emptyList(),
    val defaultCalendarId: Long? = null
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

    val uiState: StateFlow<TaskUiState> = combine(
        taskDao.getAllTasks(),
        _isLoading,
        _error,
        _calendars,
        _defaultCalendarId
    ) { tasks, isLoading, error, calendars, defaultId ->
        TaskUiState(tasks, isLoading, error, calendars, defaultId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TaskUiState(isLoading = true)
    )

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
