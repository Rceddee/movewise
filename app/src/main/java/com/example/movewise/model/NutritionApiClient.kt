package com.example.movewise.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

data class NutritionData(
    val calories: Int = 0,
    val protein: Int = 0,
    val carbs: Int = 0,
    val fat: Int = 0
) {
    fun isEmpty() = calories == 0 && protein == 0 && carbs == 0 && fat == 0
}

class NutritionApiClient {
    private val client = OkHttpClient()

    // Query Open Food Facts API (100% Free, No Auth needed) for nutrition info.
    suspend fun getEstimatedNutrition(foodName: String): NutritionData = withContext(Dispatchers.IO) {
        if (foodName.isEmpty() || foodName == "Unknown" || foodName == "background") return@withContext NutritionData()
        
        var result = NutritionData()
        
        try {
            val url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=${foodName.replace(" ", "+")}&search_simple=1&action=process&json=1&page_size=1"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (bodyStr != null) {
                        val json = JSONObject(bodyStr)
                        if (json.has("products")) {
                            val products = json.getJSONArray("products")
                            if (products.length() > 0) {
                                val product = products.getJSONObject(0)
                                val nutriments = product.optJSONObject("nutriments")
                                
                                result = NutritionData(
                                    calories = nutriments?.optInt("energy-kcal_100g", 0) ?: 0,
                                    protein = nutriments?.optInt("proteins_100g", 0) ?: 0,
                                    carbs = nutriments?.optInt("carbohydrates_100g", 0) ?: 0,
                                    fat = nutriments?.optInt("fat_100g", 0) ?: 0
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 1. Fallback to free CalorieNinja API if OpenFoodFacts fails
        if (result.isEmpty()) {
            result = getFallbackNutrition(foodName)
        }
        
        // 2. Final Fallback to hardcoded generic dictionary
        if (result.isEmpty()) {
            result = getGenericFuzzyMatch(foodName)
        }
        
        return@withContext result
    }

    private suspend fun getFallbackNutrition(query: String): NutritionData = withContext(Dispatchers.IO) {
        try {
            // Using a public proxy for testing/fallback (Edamam requires keys, but OpenFoodFacts has a text endpoint)
            // As a free app, we'll try querying the OpenFoodFacts V2 text search API which is sometimes more lenient
            val url = "https://us.openfoodfacts.org/api/v2/search?categories_tags_en=${query.replace(" ", "-")}&fields=nutriments&sort_by=popularity&page_size=1"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (bodyStr != null) {
                        val json = JSONObject(bodyStr)
                        if (json.has("products") && json.getJSONArray("products").length() > 0) {
                            val nutriments = json.getJSONArray("products").getJSONObject(0).optJSONObject("nutriments")
                            return@withContext NutritionData(
                                calories = nutriments?.optInt("energy-kcal_100g", 0) ?: 0,
                                protein = nutriments?.optInt("proteins_100g", 0) ?: 0,
                                carbs = nutriments?.optInt("carbohydrates_100g", 0) ?: 0,
                                fat = nutriments?.optInt("fat_100g", 0) ?: 0
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext NutritionData()
    }

    private fun getGenericFuzzyMatch(query: String): NutritionData {
        val q = query.lowercase(Locale.ROOT)
        // A generic database of common ML Kit vision categories that often miss barcodes
        return when {
            q.contains("apple") -> NutritionData(calories = 52, protein = 0, carbs = 14, fat = 0)
            q.contains("banana") -> NutritionData(calories = 89, protein = 1, carbs = 23, fat = 0)
            q.contains("orange") -> NutritionData(calories = 47, protein = 1, carbs = 12, fat = 0)
            q.contains("chicken") -> NutritionData(calories = 165, protein = 31, carbs = 0, fat = 3)
            q.contains("beef") || q.contains("steak") -> NutritionData(calories = 250, protein = 26, carbs = 0, fat = 15)
            q.contains("rice") -> NutritionData(calories = 130, protein = 2, carbs = 28, fat = 0)
            q.contains("pizza") -> NutritionData(calories = 266, protein = 11, carbs = 33, fat = 10)
            q.contains("burger") -> NutritionData(calories = 295, protein = 14, carbs = 24, fat = 14)
            q.contains("salad") -> NutritionData(calories = 152, protein = 5, carbs = 11, fat = 10)
            q.contains("bread") || q.contains("toast") -> NutritionData(calories = 265, protein = 9, carbs = 49, fat = 3)
            q.contains("egg") -> NutritionData(calories = 155, protein = 13, carbs = 1, fat = 11)
            q.contains("milk") -> NutritionData(calories = 42, protein = 3, carbs = 5, fat = 1)
            q.contains("cheese") -> NutritionData(calories = 402, protein = 25, carbs = 1, fat = 33)
            q.contains("water") -> NutritionData(calories = 0, protein = 0, carbs = 0, fat = 0)
            else -> NutritionData()
        }
    }

    suspend fun getNutritionByBarcode(barcode: String): NutritionData = withContext(Dispatchers.IO) {
        if (barcode.isEmpty()) return@withContext NutritionData()
        
        try {
            val url = "https://world.openfoodfacts.org/api/v0/product/$barcode.json"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext NutritionData()
                val bodyStr = response.body?.string() ?: return@withContext NutritionData()
                val json = JSONObject(bodyStr)
                if (json.has("product")) {
                    val product = json.getJSONObject("product")
                    val nutriments = product.optJSONObject("nutriments")
                    
                    return@withContext NutritionData(
                        calories = nutriments?.optInt("energy-kcal_100g", 0) ?: 0,
                        protein = nutriments?.optInt("proteins_100g", 0) ?: 0,
                        carbs = nutriments?.optInt("carbohydrates_100g", 0) ?: 0,
                        fat = nutriments?.optInt("fat_100g", 0) ?: 0
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext NutritionData()
    }

    // Keep for backward compatibility or refactor usages
    suspend fun getEstimatedCalories(foodName: String): Int {
        return getEstimatedNutrition(foodName).calories
    }
}

