package com.example.aitasklist.data.local

import androidx.room.TypeConverter
import com.example.aitasklist.model.Priority

class Converters {
    @TypeConverter
    fun fromPriority(priority: Priority): String {
        return priority.name
    }

    @TypeConverter
    fun toPriority(priority: String): Priority {
        return try {
            Priority.valueOf(priority)
        } catch (e: Exception) {
            Priority.LOW
        }
    }
}
