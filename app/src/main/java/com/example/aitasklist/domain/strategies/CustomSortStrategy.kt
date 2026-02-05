package com.example.aitasklist.domain.strategies

import com.example.aitasklist.domain.SortStrategy
import com.example.aitasklist.model.Task

class CustomSortStrategy : SortStrategy {
    override fun sort(tasks: List<Task>, ascending: Boolean): List<Task> {
        // Custom sort typically relies on an explicit order index
        // Ascending flag might not apply or could invert the list
        return if (ascending) {
             tasks.sortedBy { it.orderIndex }
        } else {
             tasks.sortedByDescending { it.orderIndex }
        }
    }
}
