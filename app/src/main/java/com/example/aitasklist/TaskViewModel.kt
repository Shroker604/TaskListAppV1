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

data class TaskUiState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GeminiRepository()
    private val taskDao = (application as TaskApplication).database.taskDao()
    
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TaskUiState> = combine(
        taskDao.getAllTasks(),
        _isLoading,
        _error
    ) { tasks, isLoading, error ->
        TaskUiState(tasks = tasks, isLoading = isLoading, error = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TaskUiState(isLoading = true)
    )

    fun generateTasks(input: String) {
        if (input.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val taskStrings = repository.parseTasks(input)
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
            }
        }
    }

    fun updateTaskDate(taskId: String, newDate: Long) {
        viewModelScope.launch {
            val task = uiState.value.tasks.find { it.id == taskId }
            task?.let {
                taskDao.updateTask(it.copy(scheduledDate = newDate))
            }
        }
    }

    fun removeCompletedTasks() {
        viewModelScope.launch {
            taskDao.deleteCompletedTasks()
        }
    }

    fun clearTasks() {
        viewModelScope.launch {
            taskDao.deleteAllTasks()
        }
    }
}
