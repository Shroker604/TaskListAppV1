package com.example.aitasklist.domain

import com.example.aitasklist.domain.strategies.CreationDateSortStrategy
import com.example.aitasklist.domain.strategies.DateReminderSortStrategy
import com.example.aitasklist.domain.strategies.CustomSortStrategy

class SortStrategyRegistry {
    private val strategies = mapOf(
        SortOption.CREATION_DATE to CreationDateSortStrategy(),
        SortOption.DATE_REMINDER to DateReminderSortStrategy(),
        SortOption.CUSTOM to CustomSortStrategy()
    )

    fun getStrategy(option: SortOption): SortStrategy {
        return strategies[option] ?: CreationDateSortStrategy() // Default fallback
    }
}
