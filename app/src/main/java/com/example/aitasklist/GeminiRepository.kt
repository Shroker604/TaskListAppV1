package com.example.aitasklist

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class GeminiRepository {
    // Use the API Key from BuildConfig
    private val apiKey = BuildConfig.GEMINI_API_KEY

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    suspend fun parseTasks(input: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are a helpful assistant. Please take the following user input and split it into a list of distinct tasks.
                
                Rules:
                1. Return ONLY a valid JSON array of strings.
                2. Do not include markdown formatting like ```json ... ```.
                3. Group related items together into a single task. For example, if the user says "Buy apples, potatoes and milk", this should be ONE task like "Buy apples, potatoes, and milk" or "Grocery shopping: apples, potatoes, milk".
                4. Keep unrelated tasks separate. For example, "Buy milk and wash the car" should be TWO tasks: "Buy milk" and "Wash the car".
                
                Input: "$input"
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val responseText = response.text?.trim() ?: "[]"
            
            // Clean up potential markdown code blocks if the model ignores the instruction
            val cleanJson = responseText.replace("```json", "").replace("```", "").trim()

            val jsonArray = JSONArray(cleanJson)
            val tasks = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                tasks.add(jsonArray.getString(i))
            }
            tasks
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: just return the input as a single task if parsing fails
            listOf("Error parsing tasks: ${e.message}")
        }
    }
}
