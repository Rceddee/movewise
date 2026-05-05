package com.example.movewise.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.movewise.R
import com.example.movewise.model.DataRepository

class RecommendationsFragment : Fragment() {
    private val repo by lazy { DataRepository.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_recommendations, container, false)
        val tvWorkoutName: TextView = view.findViewById(R.id.tv_rec_workout_name)
        val btnAccept: Button = view.findViewById(R.id.btn_accept_workout)

        val workoutsCount = repo.getWorkouts().size
        val mealsCount = repo.getMeals().size

        if (workoutsCount == 0) {
            tvWorkoutName.text = "Beginner Full Body Stretch"
        } else if (mealsCount == 0) {
            tvWorkoutName.text = "Quick 15-Min Walk & Log a Meal!"
        } else {
            tvWorkoutName.text = "HIIT Cardio Blitz"
        }

        btnAccept.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, WorkoutFragment())
                .commit()
        }

        return view
    }
}
