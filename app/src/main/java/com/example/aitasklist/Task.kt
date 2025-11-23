package com.example.aitasklist

import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isCompleted: Boolean = false
)
