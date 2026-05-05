package com.example.movewise.controller

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.movewise.R
import com.example.movewise.model.DataRepository
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry

class ProgressFragment : Fragment(), DataRepository.DataListener {
    private val repo by lazy { DataRepository.getInstance() }
    private lateinit var lineChart: LineChart
    private lateinit var pieChart: PieChart

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_progress, container, false)

        lineChart = view.findViewById(R.id.chart_weight)
        pieChart = view.findViewById(R.id.chart_activity_pie)

        view.findViewById<Button>(R.id.btn_log_weight)?.setOnClickListener {
            showLogWeightDialog()
        }

        view.findViewById<View>(R.id.btn_view_badges).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, BadgesFragment())
                .addToBackStack(null)
                .commit()
        }

        repo.addListener(this)
        updateCharts()
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        repo.removeListener(this)
    }

    override fun onDataChanged() {
        if (isAdded) {
            updateCharts()
        }
    }

    private fun updateCharts() {
        setupWeightChart()
        setupActivityPieChart()
    }

    private fun showLogWeightDialog() {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.hint = "Weight in kg (e.g., 75.5)"

        AlertDialog.Builder(requireContext())
            .setTitle("Log Weight")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val weightStr = input.text.toString().trim()
                val weight = weightStr.toFloatOrNull()
                
                if (weight != null && weight > 0) {
                    repo.addWeight(weight)
                    updateCharts()
                } else {
                    Toast.makeText(requireContext(), "Please enter a valid weight", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupWeightChart() {
        val history = repo.getWeightHistory()
        val entries = ArrayList<Entry>()
        
        if (history.isEmpty()) {
            entries.add(Entry(0f, 0f))
        } else {
            history.forEachIndexed { index, weightLog ->
                entries.add(Entry(index.toFloat(), weightLog.weight))
            }
        }

        val dataSet = LineDataSet(entries, "Weight (kg)")
        dataSet.color = Color.parseColor("#3B82F6")
        dataSet.setCircleColor(Color.parseColor("#A855F7"))
        dataSet.lineWidth = 2f
        
        lineChart.data = LineData(dataSet)
        lineChart.description.isEnabled = false
        lineChart.animateY(500)
        lineChart.invalidate()
    }

    private fun setupActivityPieChart() {
        val workouts = repo.getWorkouts()
        val typeCount = workouts.groupingBy { it.type }.eachCount()

        val entries = ArrayList<PieEntry>()
        if (typeCount.isEmpty()) {
            entries.add(PieEntry(1f, "No Data"))
        } else {
            typeCount.forEach { (type, count) ->
                entries.add(PieEntry(count.toFloat(), type))
            }
        }

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(
            Color.parseColor("#A855F7"),
            Color.parseColor("#3B82F6"),
            Color.parseColor("#F59E0B"),
            Color.parseColor("#10B981"),
            Color.parseColor("#EF4444")
        )
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 14f

        pieChart.data = PieData(dataSet)
        pieChart.description.isEnabled = false
        pieChart.centerText = "Activity"
        pieChart.setCenterTextSize(18f)
        pieChart.animateY(500)
        pieChart.invalidate()
    }
}
