package com.example.movewise.controller

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movewise.R
import com.example.movewise.model.DataRepository
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class DashboardFragment : Fragment(), SensorEventListener, DataRepository.DataListener {
    private lateinit var lineChart: LineChart
    private val repo by lazy { DataRepository.getInstance() }
    
    // Step Sensor
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var initialStepCount = -1f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)

        lineChart = view.findViewById(R.id.line_chart_steps)
        
        view.findViewById<Button>(R.id.btn_add_steps)?.setOnClickListener {
            showAddStepsDialog()
        }

        view.findViewById<View>(R.id.btn_view_recommendations).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, RecommendationsFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<View>(R.id.btn_start_workout).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, WorkoutFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<View>(R.id.btn_settings)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, SettingsFragment())
                .addToBackStack(null)
                .commit()
        }
        
        initStepSensor()
        repo.addListener(this)
        updateDashboardData(view)

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        repo.removeListener(this)
    }

    override fun onDataChanged() {
        if (isAdded) {
            view?.let { updateDashboardData(it) }
        }
    }
    
    private fun initStepSensor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 101)
            }
        }
        
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        
        if (stepSensor == null) {
            Log.d("StepSensor", "Hardware step counter not available")
        }
    }

    override fun onResume() {
        super.onResume()
        repo.updateStreak()
        stepSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        view?.let { updateDashboardData(it) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0]
            if (initialStepCount == -1f) {
                initialStepCount = totalSteps
            }
            val currentSessionSteps = totalSteps - initialStepCount
            val savedSteps = repo.getDailySteps()
            
            // Increment the baseline so we don't double count if user manually adds some
            // For a simple demo, we'll just update if it's higher
            if (currentSessionSteps > 0) {
               // In a real app, logic would be more complex to handle reboot/reset
               // repo.saveDailySteps(savedSteps + currentSessionSteps)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateDashboardData(view: View) {
        setupChart(view.findViewById(R.id.line_chart_steps))
        
        // Update summary metrics
        val meals = repo.getMeals()
        val totalCalories = meals.sumOf { it.calories }
        view.findViewById<TextView>(R.id.tv_calories).text = totalCalories.toString()
        
        // FIX: Display current day's steps
        val steps = repo.getDailySteps().toInt()
        view.findViewById<TextView>(R.id.tv_steps).text = steps.toString()
        
        // FIX: Display today's active minutes
        val activeMin = repo.getTodayActiveMinutes()
        view.findViewById<TextView>(R.id.tv_active_min).text = activeMin.toString()
        
        val workouts = repo.getWorkouts()
        view.findViewById<TextView>(R.id.tv_workouts).text = workouts.size.toString()

        view.findViewById<TextView>(R.id.tv_streak_count).text = repo.getStreak().toString()
        
        // FIX: Display today's total reps
        val totalReps = repo.getTodayTotalReps()
        view.findViewById<TextView>(R.id.tv_total_reps).text = totalReps.toString()

        // Generate AI Insight
        val tvInsight = view.findViewById<TextView>(R.id.tv_ai_insight)
        tvInsight.text = generateHealthInsight(steps, totalCalories, activeMin)

        // Update workout history RecyclerView
        val rvHistory = view.findViewById<RecyclerView>(R.id.rv_workout_history)
        val tvEmpty = view.findViewById<TextView>(R.id.tv_history_empty)

        if (workouts.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvHistory.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvHistory.visibility = View.VISIBLE
            rvHistory.layoutManager = LinearLayoutManager(context)
            rvHistory.adapter = WorkoutHistoryAdapter(workouts.takeLast(3).reversed())
        }
    }

    private fun showAddStepsDialog() {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "e.g., 5000"

        AlertDialog.Builder(requireContext())
            .setTitle("Log Today's Steps")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val stepsStr = input.text.toString().trim()
                val steps = stepsStr.toFloatOrNull()
                
                if (steps != null && steps >= 0) {
                    val current = repo.getDailySteps()
                    repo.saveDailySteps(current + steps)
                } else {
                    Toast.makeText(requireContext(), "Please enter a valid step count", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupChart(lineChart: LineChart) {
        val history = repo.getStepHistory()
        val entries = ArrayList<Entry>()
        
        if (history.isEmpty()) {
            entries.add(Entry(0f, 0f))
        } else {
            var index = 0f
            history.toList()
                .sortedBy { it.first }
                .takeLast(7)
                .forEach { (_, steps) ->
                    entries.add(Entry(index++, steps))
                }
        }

        val dataSet = LineDataSet(entries, "Daily Steps")
        dataSet.color = ContextCompat.getColor(requireContext(), R.color.brand_lavender)
        dataSet.setCircleColor(ContextCompat.getColor(requireContext(), R.color.brand_sky))
        dataSet.lineWidth = 3f
        dataSet.circleRadius = 5f
        dataSet.setDrawCircleHole(true)
        dataSet.circleHoleColor = Color.WHITE
        dataSet.valueTextSize = 10f
        dataSet.setDrawFilled(true)
        dataSet.fillDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.bg_gradient_brand)
        dataSet.fillAlpha = 50

        val lineData = LineData(dataSet)
        lineChart.data = lineData
        lineChart.description.isEnabled = false
        lineChart.xAxis.setDrawGridLines(false)
        lineChart.axisLeft.setDrawGridLines(false)
        lineChart.axisRight.isEnabled = false
        lineChart.xAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_sub)
        lineChart.axisLeft.textColor = ContextCompat.getColor(requireContext(), R.color.text_sub)
        lineChart.legend.textColor = ContextCompat.getColor(requireContext(), R.color.text_main)
        lineChart.animateX(800)
        lineChart.invalidate()
    }

    private fun generateHealthInsight(steps: Int, calories: Int, activeMin: Int): String {
        val sb = StringBuilder()
        
        // Step insight
        if (steps < 3000) sb.append("You've taken $steps steps. Let's aim for a quick 15-minute walk to hit 5,000! 🚶\n\n")
        else if (steps < 8000) sb.append("Great job! You're at $steps steps. Just a bit more to hit the 10k milestone! 🚀\n\n")
        else sb.append("Elite movement! $steps steps is fantastic consistency. Keep it up! 🏆\n\n")
        
        // Active minutes insight
        if (activeMin == 0) sb.append("No workouts yet today? Even a 5-minute stretch makes a difference. 🧘")
        else if (activeMin < 30) sb.append("You've spent $activeMin minutes being active. You're doing great, keep that momentum!")
        else sb.append("Incredible session! $activeMin minutes of activity shows real dedication.")
        
        return sb.toString()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initStepSensor()
        }
    }
}
