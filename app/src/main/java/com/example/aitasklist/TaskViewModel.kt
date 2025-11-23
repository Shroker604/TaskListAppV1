package com.example.aitasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TaskUiState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class TaskViewModel : ViewModel() {
    private val repository = GeminiRepository()
    
    private val _uiState = MutableStateFlow(TaskUiState())
    val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

    fun generateTasks(input: String) {
        if (input.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val taskStrings = repository.parseTasks(input)
                val newTasks = taskStrings.map { Task(content = it) }
                // Append new tasks to existing ones
                _uiState.value = _uiState.value.copy(
                    tasks = _uiState.value.tasks + newTasks,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun toggleTaskCompletion(taskId: String) {
        val updatedTasks = _uiState.value.tasks.map { task ->
            if (task.id == taskId) {
                task.copy(isCompleted = !task.isCompleted)
            } else {
                task
            }
        }
        _uiState.value = _uiState.value.copy(tasks = updatedTasks)
    }

    fun removeCompletedTasks() {
        val activeTasks = _uiState.value.tasks.filter { !it.isCompleted }
        _uiState.value = _uiState.value.copy(tasks = activeTasks)
    }


    
    fun clearTasks() {
        _uiState.value = TaskUiState()
    }
}
