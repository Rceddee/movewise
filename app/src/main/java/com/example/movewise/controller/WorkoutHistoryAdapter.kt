package com.example.movewise.controller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movewise.R
import com.example.movewise.model.WorkoutLog

class WorkoutHistoryAdapter(private val workouts: List<WorkoutLog>) :
    RecyclerView.Adapter<WorkoutHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tv_history_type)
        val tvInfo: TextView = view.findViewById(R.id.tv_history_info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val workout = workouts[position]
        holder.tvType.text = workout.type
        val repInfo = if (workout.reps > 0) " · ${workout.reps} reps" else ""
        holder.tvInfo.text = "${workout.durationMinutes} min$repInfo"
    }

    override fun getItemCount() = workouts.size
}
