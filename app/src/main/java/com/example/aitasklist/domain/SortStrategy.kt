package com.example.aitasklist.domain

import com.example.aitasklist.model.Task

interface SortStrategy {
    fun sort(tasks: List<Task>, ascending: Boolean): List<Task>
}
