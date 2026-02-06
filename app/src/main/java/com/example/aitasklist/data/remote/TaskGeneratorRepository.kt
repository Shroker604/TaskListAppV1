package com.example.aitasklist.data.remote

interface TaskGeneratorRepository {
    suspend fun parseTasks(input: String, splitTasks: Boolean): List<String>
}
