package com.example.aitasklist.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val scheduledDate: Long = System.currentTimeMillis(),
    val calendarEventId: Long? = null,
    val reminderTime: Long? = null
)
