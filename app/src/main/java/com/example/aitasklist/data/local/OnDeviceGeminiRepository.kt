package com.example.aitasklist.data.local

import android.content.Context
import android.util.Log
import com.example.aitasklist.data.remote.TaskGeneratorRepository

import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class OnDeviceGeminiRepository(private val context: Context) : TaskGeneratorRepository {

    private var generativeModel: GenerativeModel? = null

    private fun getModel(): GenerativeModel {
        if (generativeModel == null) {
             // Generation.getClient() takes no arguments for default config.
             // Context is managed internally by the SDK initialization.
            generativeModel = Generation.getClient()
        }
        return generativeModel!!
    }

    override suspend fun parseTasks(input: String, splitTasks: Boolean): List<String> = withContext(Dispatchers.IO) {
        try {
            val model = getModel()

            val prompt = if (splitTasks) {
                """
                You are a task parser. Split the input into individual tasks.
                Rule 1: Split items connected by "and", "or", or ",".
                Rule 2: Remove conversational filler (e.g., "I need to", "Please", "Today").
                Example Input: "Today I need to buy milk and eggs"
                Example Output: ["Buy milk", "Buy eggs"]
                Return ONLY a JSON array of strings.
                Input: "$input"
                Output JSON:
                """.trimIndent()
            } else {
                """
                You are a task parser. Keep lists of items together.
                Rule 1: Group items that belong to the same action (e.g., "buy A, B, and C" is ONE task).
                Rule 2: Split ONLY distinct actions (e.g., "Get gas" and "Call Mark" are TWO tasks).
                Rule 3: Remove conversational filler.
                Example Input: "I need to get gas and also buy milk, eggs, and bread"
                Example Output: ["Get gas", "Buy milk, eggs, and bread"]
                Return ONLY a JSON array of strings.
                Input: "$input"
                Output JSON:
                """.trimIndent()
            }
               // generateContent returns GenerateContentResponse directly (blocking or suspend)
            val response = model.generateContent(prompt)
            
            // EXTRACT TEXT USING REFLECTION (Safe against API class visibility issues)
            val responseText = try {
                // 1. getCandidates()
                val getCandidatesMethod = response.javaClass.getMethod("getCandidates")
                val candidates = getCandidatesMethod.invoke(response) as? List<*>
                
                if (candidates.isNullOrEmpty()) {
                    "No candidates found"
                } else {
                    val firstCandidate = candidates[0]!!
                    
                    // 2. getText() directly from Candidate (Found via introspection)
                    // The beta library seems to flatten this or provide a convenience method
                    val getTextMethod = firstCandidate.javaClass.getMethod("getText")
                    getTextMethod.invoke(firstCandidate) as? String ?: "[]"
                }
            } catch (e: Exception) {
                 Log.e("OnDeviceAI", "Reflection Failed", e)
                 "Reflection Error: ${e.message}"
            }
            
            Log.d("OnDeviceAI", "Extracted Text: $responseText")
            
            val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
            
             if (!cleanJson.startsWith("[")) {
                 // Return raw response for debugging if not JSON
                 return@withContext listOf(cleanJson)
            }

            val jsonArray = JSONArray(cleanJson)
            val tasks = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.opt(i)
                 // Handle {"task": "Buy milk"} or just "Buy milk"
                if (item is org.json.JSONObject) {
                    val taskContent = item.optString("task")
                    if (taskContent.isNotEmpty()) {
                         tasks.add(taskContent)
                    } else {
                        // Fallback to values if 'task' key missing
                         tasks.add(item.toString())
                    }
                } else {
                    tasks.add(item.toString())
                }
            }
            tasks

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to Cloud or just report error
            listOf("On-Device Error: ${e.message}")
        }
    }
}
