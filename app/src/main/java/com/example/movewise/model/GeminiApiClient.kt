package com.example.movewise.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

import com.example.movewise.BuildConfig

class GeminiApiClient {
    private val API_KEY = BuildConfig.GEMINI_API_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getChatResponse(
        userMessage: String,
        persona: ChatBotPersona,
        history: List<ChatMessage>
    ): String = withContext(Dispatchers.IO) {

        if (API_KEY == "YOUR_API_KEY_HERE" || API_KEY.isEmpty()) {
            return@withContext "API Key missing! Please open GeminiApiClient.kt and paste your free Google AI Studio API key at the top to activate me."
        }

        try {
            val contentsArray = JSONArray()

            // Inject persona as first user/model pair (system instruction workaround)
            val personaText = """
                You are ${persona.name}, an elite AI Health & Fitness Coach within the MoveWise app. 
                Your goals:
                - Tone: ${persona.tone}.
                - Focus: ${persona.focus}.
                - Style: Be encouraging, professional, and data-driven. 
                - MoveWise context: We track steps, calories, workouts (including reps via AI Camera), and nutrition.
                - Format: Use bullet points for advice. Keep responses concise but impactful.
            """.trimIndent()
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", personaText)))
            })
            contentsArray.put(JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().put(JSONObject().put("text", "Understood! I'll act as your ${persona.name} assistant.")))
            })

            // Add conversation history
            history.forEach { msg ->
                contentsArray.put(JSONObject().apply {
                    put("role", if (msg.fromUser) "user" else "model")
                    put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                })
            }

            // Add current user message
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
            })

            val requestBody = JSONObject().apply {
                put("contents", contentsArray)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=$API_KEY")
                .post(requestBody)
                .build()

            val responseBody = client.newCall(request).execute().body?.string()
                ?: return@withContext "No response from AI."

            val json = JSONObject(responseBody)
            
            // Check for errors
            if (json.has("error")) {
                val errorMsg = json.getJSONObject("error").optString("message", "Unknown error")
                return@withContext "AI Error: $errorMsg"
            }

            val text = json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            return@withContext text
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Error connecting to AI: ${e.localizedMessage}"
        }
    }
}
