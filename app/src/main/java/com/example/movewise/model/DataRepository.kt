package com.example.movewise.model

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.movewise.model.Badge
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DataRepository private constructor(context: Context, uid: String) {
    private val prefs: SharedPreferences = context.getSharedPreferences("MoveWiseData_$uid", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val database by lazy { 
        try {
            FirebaseDatabase.getInstance("https://movewise-fa69f-default-rtdb.firebaseio.com/").reference
        } catch (e: Exception) {
            Log.e("DataRepository", "Firebase not initialized, database unavailable")
            null
        }
    }

    private val listeners = mutableSetOf<DataListener>()

    interface DataListener {
        fun onDataChanged()
    }

    fun addListener(listener: DataListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: DataListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            listeners.forEach { it.onDataChanged() }
        }
    }

    init {
        syncFromFirebase()
    }

    private fun getUserId(): String? {
        return try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: Exception) {
            null
        }
    }

    private fun syncToFirebase(key: String, data: Any) {
        val uid = getUserId() ?: return
        database?.child("users")?.child(uid)?.child(key)?.setValue(data)
            ?.addOnFailureListener { e -> Log.e("DataRepository", "Firebase sync failed for key $key", e) }
    }

    private fun syncFromFirebase() {
        val uid = getUserId() ?: return
        database?.child("users")?.child(uid)?.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    val map = snapshot.value as? Map<String, Any> ?: return
                    val editor = prefs.edit()
                    
                    map.forEach { (key, value) ->
                        when {
                            value is List<*> || value is Map<*, *> -> {
                                editor.putString(key, gson.toJson(value))
                            }
                            value is Long -> editor.putLong(key, value)
                            value is Int -> editor.putInt(key, value)
                            value is Float -> editor.putFloat(key, value)
                            value is String -> editor.putString(key, value)
                            value is Boolean -> editor.putBoolean(key, value)
                        }
                    }
                    editor.apply()
                    notifyListeners()
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("DataRepository", "Firebase listener cancelled", error.toException())
            }
        })
    }

    companion object {
        @Volatile private var instance: DataRepository? = null

        fun init(context: Context, uid: String) {
            synchronized(this) {
                if (instance == null) {
                    instance = DataRepository(context.applicationContext, uid)
                }
            }
        }

        fun getInstance(): DataRepository {
            return instance ?: throw IllegalStateException("DataRepository is not initialized, call init(Context, String) first.")
        }

        fun reset() {
            instance = null
        }
    }

    // --- STEP DATA ---
    fun saveDailySteps(steps: Float) {
        val today = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
        prefs.edit().putFloat("steps_$today", steps).apply()
        
        val history = getStepHistory().toMutableMap()
        history[today.toString()] = steps
        prefs.edit().putString("step_history", gson.toJson(history)).apply()
        
        syncToFirebase("step_history", history)
        notifyListeners()
    }

    fun getDailySteps(): Float {
        val today = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
        return prefs.getFloat("steps_$today", 0f)
    }

    fun getStepHistory(): Map<String, Float> {
        val json = prefs.getString("step_history", null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Float>>() {}.type
        return gson.fromJson(json, type)
    }

    // --- MEAL DATA ---
    fun addMeal(meal: Meal) {
        val meals = getMeals().toMutableList()
        meals.add(meal)
        prefs.edit().putString("meals", gson.toJson(meals)).apply()
        syncToFirebase("meals", meals)
        notifyListeners()
    }

    fun getMeals(): List<Meal> {
        val json = prefs.getString("meals", null) ?: return emptyList()
        val type = object : TypeToken<List<Meal>>() {}.type
        return gson.fromJson(json, type)
    }

    // --- WORKOUT DATA ---
    fun addWorkout(type: String, durationMinutes: Int, reps: Int = 0) {
        val workouts = getWorkouts().toMutableList()
        workouts.add(WorkoutLog(System.currentTimeMillis(), type, durationMinutes, reps))
        prefs.edit().putString("workouts", gson.toJson(workouts)).apply()
        syncToFirebase("workouts", workouts)
        notifyListeners()
    }

    fun getWorkouts(): List<WorkoutLog> {
        val json = prefs.getString("workouts", null) ?: return emptyList()
        val type = object : TypeToken<List<WorkoutLog>>() {}.type
        return gson.fromJson(json, type)
    }

    fun getTodayActiveMinutes(): Int {
        val workouts = getWorkouts()
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis
        return workouts.filter { it.timestamp >= todayStart }.sumOf { it.durationMinutes }
    }

    fun getTodayTotalReps(): Int {
        val workouts = getWorkouts()
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis
        return workouts.filter { it.timestamp >= todayStart }.sumOf { it.reps }
    }

    // --- WEIGHT DATA ---
    fun addWeight(weight: Float) {
        val history = getWeightHistory().toMutableList()
        history.add(WeightLog(System.currentTimeMillis(), weight))
        prefs.edit().putString("weight_history", gson.toJson(history)).apply()
        syncToFirebase("weight_history", history)
        notifyListeners()
    }

    fun getWeightHistory(): List<WeightLog> {
        val json = prefs.getString("weight_history", null) ?: return emptyList()
        val type = object : TypeToken<List<WeightLog>>() {}.type
        return gson.fromJson(json, type)
    }

    // --- CHAT DATA ---
    fun saveMessages(messages: List<ChatMessage>) {
        prefs.edit().putString("chat_messages", gson.toJson(messages)).apply()
        syncToFirebase("chat_messages", messages)
        notifyListeners()
    }

    fun getMessages(): List<ChatMessage> {
        val json = prefs.getString("chat_messages", null) ?: return emptyList()
        val type = object : TypeToken<List<ChatMessage>>() {}.type
        return gson.fromJson(json, type)
    }

    // --- STREAK DATA ---
    private fun getStreakSafe(): Int {
        // Firebase sync stores numbers as Long; guard against ClassCastException
        return try { prefs.getInt("daily_streak", 0) } catch (e: ClassCastException) {
            prefs.getLong("daily_streak", 0).toInt().also { v ->
                // Rewrite as Int so future reads succeed
                prefs.edit().putInt("daily_streak", v).apply()
            }
        }
    }

    fun getStreak(): Int {
        val lastUpdate = prefs.getLong("streak_last_update", 0)
        val today = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
        val streak = getStreakSafe()

        return when {
            lastUpdate == today -> streak
            lastUpdate == today - 1 -> streak
            else -> 0  // Streak broken
        }
    }

    fun updateStreak() {
        val lastUpdate = prefs.getLong("streak_last_update", 0)
        val today = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
        var streak = getStreakSafe()

        if (lastUpdate < today) {
            streak = if (lastUpdate == today - 1) streak + 1 else 1
            prefs.edit().putInt("daily_streak", streak)
                .putLong("streak_last_update", today).apply()
            syncToFirebase("daily_streak", streak)
            notifyListeners()
        }
    }

    fun savePersona(persona: ChatBotPersona) {
        prefs.edit().putString("persona", gson.toJson(persona)).apply()
        syncToFirebase("persona", persona)
        notifyListeners()
    }

    fun getPersona(): ChatBotPersona {
        val json = prefs.getString("persona", null) ?: return ChatBotPersona()
        return gson.fromJson(json, ChatBotPersona::class.java)
    }

    // --- WATER DATA ---
    fun saveWaterIntake(ml: Int) {
        val today = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
        prefs.edit().putInt("water_$today", ml).apply()
        syncToFirebase("water_$today", ml)
        notifyListeners()
    }

    fun getWaterIntake(): Int {
        val today = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
        return try {
            prefs.getInt("water_$today", 0)
        } catch (e: ClassCastException) {
            prefs.getLong("water_$today", 0).toInt().also { v ->
                prefs.edit().putInt("water_$today", v).apply()
            }
        }
    }
}

data class WorkoutLog(
    val timestamp: Long, 
    val type: String, 
    val durationMinutes: Int,
    val reps: Int = 0
)
data class WeightLog(val timestamp: Long, val weight: Float)
