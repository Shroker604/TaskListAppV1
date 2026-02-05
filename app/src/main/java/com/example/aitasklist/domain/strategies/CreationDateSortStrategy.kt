package com.example.aitasklist.domain.strategies

import com.example.aitasklist.domain.SortStrategy
import com.example.aitasklist.model.Task

class CreationDateSortStrategy : SortStrategy {
    override fun sort(tasks: List<Task>, ascending: Boolean): List<Task> {
        val comparator = Comparator<Task> { t1, t2 ->
            // 1. Completion Status (Active first)
            if (t1.isCompleted != t2.isCompleted) {
                if (t1.isCompleted) 1 else -1
            } else {
                // 2. Creation Date
                if (ascending) {
                    t1.createdAt.compareTo(t2.createdAt)
                } else {
                    t2.createdAt.compareTo(t1.createdAt)
                }
            }
        }
        return tasks.sortedWith(comparator)
    }
}
