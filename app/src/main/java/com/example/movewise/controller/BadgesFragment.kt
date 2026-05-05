package com.example.movewise.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movewise.R
import com.example.movewise.model.Badge
import com.example.movewise.model.DataRepository

class BadgesFragment : Fragment() {
    private val repo by lazy { DataRepository.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_badges, container, false)
        val rvBadges: RecyclerView = view.findViewById(R.id.rv_badges)
        
        rvBadges.layoutManager = LinearLayoutManager(context)
        
        val meals = repo.getMeals()
        val workouts = repo.getWorkouts()
        val steps = repo.getDailySteps()
        
        val actualBadges = listOf(
            Badge("First Workout", "Log your first workout", workouts.isNotEmpty(), 0),
            Badge("Step Master", "Reach 10,000 steps in a day", steps >= 10000f, 0),
            Badge("Nutritionist", "Log at least 3 meals", meals.size >= 3, 0),
            Badge("Consistency King", "Log 5 workouts", workouts.size >= 5, 0)
        )
        
        rvBadges.adapter = BadgeAdapter(actualBadges)
        
        return view
    }
}
