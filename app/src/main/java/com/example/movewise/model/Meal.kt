package com.example.movewise.model

data class Meal(
    val name: String,
    val calories: Int,
    val protein: Int = 0,
    val carbs: Int = 0,
    val fat: Int = 0,
    val time: String,
    val type: String, // Breakfast, Lunch, Dinner, Snack
    val imageUri: String? = null
)
