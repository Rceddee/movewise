package com.example.movewise.controller

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movewise.R
import com.example.movewise.model.Meal

class MealAdapter(private val meals: List<Meal>) :
    RecyclerView.Adapter<MealAdapter.MealViewHolder>() {

    class MealViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_meal_name)
        val tvTime: TextView = view.findViewById(R.id.tv_meal_time)
        val tvCalories: TextView = view.findViewById(R.id.tv_meal_calories)
        val tvMacros: TextView = view.findViewById(R.id.tv_meal_macros)
        val ivIcon: ImageView = view.findViewById(R.id.iv_meal_icon)
        val cardImage: View = view.findViewById(R.id.card_meal_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_meal, parent, false)
        return MealViewHolder(view)
    }

    override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
        val meal = meals[position]
        holder.tvName.text = meal.name
        holder.tvTime.text = meal.time
        holder.tvCalories.text = String.format("%,d kcal", meal.calories)
        
        if (meal.calories > 0) {
            holder.tvMacros.visibility = View.VISIBLE
            holder.tvMacros.text = "P: ${meal.protein}g · C: ${meal.carbs}g · F: ${meal.fat}g"
        } else {
            holder.tvMacros.visibility = View.GONE
        }

        if (!meal.imageUri.isNullOrEmpty()) {
            try {
                holder.ivIcon.setImageURI(Uri.parse(meal.imageUri))
                holder.ivIcon.scaleType = ImageView.ScaleType.CENTER_CROP
            } catch (e: Exception) {
                holder.ivIcon.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        } else {
            // Use meal type icons or default
            val iconRes = when(meal.type.lowercase()) {
                "breakfast" -> android.R.drawable.ic_menu_today
                "lunch" -> android.R.drawable.ic_menu_day
                "dinner" -> android.R.drawable.ic_menu_recent_history
                else -> android.R.drawable.ic_menu_gallery
            }
            holder.ivIcon.setImageResource(iconRes)
            holder.ivIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
            holder.ivIcon.setPadding(20, 20, 20, 20)
        }
    }

    override fun getItemCount(): Int = meals.size
}
