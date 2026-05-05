package com.example.movewise.controller

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movewise.R
import com.example.movewise.model.DataRepository
import com.example.movewise.model.Meal
import com.example.movewise.model.NutritionApiClient
import com.example.movewise.model.NutritionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NutritionFragment : Fragment(), DataRepository.DataListener {
    private val repo by lazy { DataRepository.getInstance() }
    private lateinit var rvMeals: RecyclerView
    
    private lateinit var tvCaloriesRemaining: TextView
    private lateinit var tvConsumedVal: TextView
    private lateinit var tvBurnedVal: TextView
    private lateinit var progressCaloriesCircular: com.google.android.material.progressindicator.CircularProgressIndicator
    
    private lateinit var tvProteinVal: TextView
    private lateinit var tvCarbsVal: TextView
    private lateinit var tvFatVal: TextView
    
    private lateinit var progressProtein: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var progressCarbs: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var progressFat: com.google.android.material.progressindicator.LinearProgressIndicator
    
    private lateinit var tvWaterCount: TextView
    private lateinit var btnAddWater: Button

    private var cameraExecutor: ExecutorService? = null
    private var imageClassifierHelper: ImageClassifierHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_nutrition, container, false)

        rvMeals = view.findViewById(R.id.rv_meals)
        tvCaloriesRemaining = view.findViewById(R.id.tv_calories_remaining)
        tvConsumedVal = view.findViewById(R.id.tv_consumed_val)
        tvBurnedVal = view.findViewById(R.id.tv_burned_val)
        progressCaloriesCircular = view.findViewById(R.id.progress_calories_circular)
        
        tvProteinVal = view.findViewById(R.id.tv_protein_val)
        tvCarbsVal = view.findViewById(R.id.tv_carbs_val)
        tvFatVal = view.findViewById(R.id.tv_fat_val)
        
        progressProtein = view.findViewById(R.id.progress_protein)
        progressCarbs = view.findViewById(R.id.progress_carbs)
        progressFat = view.findViewById(R.id.progress_fat)
        
        tvWaterCount = view.findViewById(R.id.tv_water_count)
        btnAddWater = view.findViewById(R.id.btn_add_water)

        btnAddWater.setOnClickListener {
            val current = repo.getWaterIntake()
            repo.saveWaterIntake(current + 250)
        }
        
        rvMeals.layoutManager = LinearLayoutManager(context)

        view.findViewById<Button>(R.id.btn_log_meal)?.setOnClickListener {
            showAddMealDialog(NutritionData(), "", null)
        }

        view.findViewById<Button>(R.id.btn_scan_barcode)?.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openBarcodeScannerDialog()
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 11)
            }
        }

        view.findViewById<Button>(R.id.btn_scan_meal)?.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openScannerDialog()
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 10)
            }
        }

        repo.addListener(this)
        updateUI()
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        repo.removeListener(this)
    }

    override fun onDataChanged() {
        if (isAdded) {
            updateUI()
        }
    }

    private fun updateUI() {
        updateMealsList()
        updateWaterIntakeDisplay()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == 10) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openScannerDialog()
            } else {
                Toast.makeText(context, "Camera permission required to scan meals.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openScannerDialog() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_camera_scanner)

        val previewView: PreviewView = dialog.findViewById(R.id.preview_view_scanner)
        val tvPrediction: TextView = dialog.findViewById(R.id.tv_prediction)
        val pbLoading: ProgressBar = dialog.findViewById(R.id.pb_loading_calories)
        val btnCancel: Button = dialog.findViewById(R.id.btn_cancel_scan)
        val btnCapture: Button = dialog.findViewById(R.id.btn_capture_meal)
        
        var currentPrediction = ""
        
        imageClassifierHelper = ImageClassifierHelper(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        var imageCapture: ImageCapture? = null

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor!!) { imageProxy ->
                        val predictionLabel = imageClassifierHelper?.classify(imageProxy, imageProxy.imageInfo.rotationDegrees) ?: "Unknown"
                        
                        val cleanPrediction = predictionLabel.split(",").first().capitalize(Locale.ROOT)
                        activity?.runOnUiThread {
                            currentPrediction = cleanPrediction
                            tvPrediction.text = "Recognized: $cleanPrediction"
                        }
                    }
                }
                
            imageCapture = ImageCapture.Builder().build()
                
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageAnalyzer, imageCapture)
            } catch (e: Exception) {
                Log.e("Scanner", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))

        btnCancel.setOnClickListener {
            cleanupCamera()
            dialog.dismiss()
        }

        btnCapture.setOnClickListener {
            if (imageCapture == null) return@setOnClickListener

            pbLoading.visibility = View.VISIBLE
            btnCapture.isEnabled = false
            btnCancel.isEnabled = false
            
            val photoFile = File(requireContext().filesDir, "meal_${System.currentTimeMillis()}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture!!.takePicture(
                outputOptions, 
                ContextCompat.getMainExecutor(requireContext()), 
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = Uri.fromFile(photoFile).toString()
                        processCapturedMeal(currentPrediction, savedUri, dialog)
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Log.e("Scanner", "Photo capture failed: ${exc.message}", exc)
                        processCapturedMeal(currentPrediction, null, dialog)
                    }
                }
            )
        }
        
        dialog.setOnDismissListener { cleanupCamera() }
        dialog.show()
    }

    private fun openBarcodeScannerDialog() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_camera_scanner) // Reuse same layout

        val previewView: PreviewView = dialog.findViewById(R.id.preview_view_scanner)
        val tvPrediction: TextView = dialog.findViewById(R.id.tv_prediction)
        val pbLoading: ProgressBar = dialog.findViewById(R.id.pb_loading_calories)
        val btnCancel: Button = dialog.findViewById(R.id.btn_cancel_scan)
        val btnCapture: Button = dialog.findViewById(R.id.btn_capture_meal)
        
        btnCapture.visibility = View.GONE // Barcode is auto-detected
        tvPrediction.text = "Align barcode within frame"
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor!!) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    if (barcodes.isNotEmpty()) {
                                        val barcode = barcodes[0].rawValue ?: ""
                                        activity?.runOnUiThread {
                                            tvPrediction.text = "Barcode: $barcode"
                                            pbLoading.visibility = View.VISIBLE
                                            processBarcode(barcode, dialog)
                                        }
                                        scanner.close()
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }
                }
                
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
            } catch (e: Exception) {
                Log.e("Scanner", "Barcode bind failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.setOnDismissListener { cleanupCamera() }
        dialog.show()
    }

    private fun processBarcode(barcode: String, dialog: Dialog) {
        lifecycleScope.launch {
            val api = NutritionApiClient()
            val nutrition = api.getNutritionByBarcode(barcode)
            
            withContext(Dispatchers.Main) {
                dialog.dismiss()
                if (nutrition.calories > 0) {
                    showAddMealDialog(nutrition, "Product $barcode", null)
                } else {
                    Toast.makeText(context, "Barcode not found in database. Please enter manually.", Toast.LENGTH_LONG).show()
                    showAddMealDialog(NutritionData(), "Unknown Product", null)
                }
            }
        }
    }

    private fun processCapturedMeal(prediction: String, imageUri: String?, dialog: Dialog) {
        lifecycleScope.launch {
            val api = NutritionApiClient()
            val nutrition = api.getEstimatedNutrition(prediction)
            
            withContext(Dispatchers.Main) {
                cleanupCamera()
                dialog.dismiss()
                
                if (nutrition.calories > 0) {
                    showAddMealDialog(nutrition, prediction, imageUri)
                } else {
                    Toast.makeText(context, "Could not find nutrition for '$prediction'. Please enter manually.", Toast.LENGTH_LONG).show()
                    showAddMealDialog(NutritionData(), prediction, imageUri)
                }
            }
        }
    }

    private fun cleanupCamera() {
        cameraExecutor?.shutdown()
        imageClassifierHelper?.close()
        context?.let { ctx ->
            try {
                ProcessCameraProvider.getInstance(ctx).get().unbindAll()
            } catch (e: Exception) {}
        }
    }

    private fun updateMealsList() {
        val meals = repo.getMeals()
        
        if (meals.isEmpty()) {
            val emptyList = listOf(Meal("No meals logged yet today.", 0, 0, 0, 0, "", ""))
            rvMeals.adapter = MealAdapter(emptyList)
        } else {
            rvMeals.adapter = MealAdapter(meals)
        }
        
        val consumed = meals.sumOf { it.calories }
        val proteinTotal = meals.sumOf { it.protein }
        val carbsTotal = meals.sumOf { it.carbs }
        val fatTotal = meals.sumOf { it.fat }
        
        val goal = 2500
        val remaining = (goal - consumed).coerceAtLeast(0)
        
        tvCaloriesRemaining.text = String.format("%,d", remaining)
        tvConsumedVal.text = String.format("%,d", consumed)
        
        val progress = (consumed * 100 / goal).coerceAtMost(100)
        progressCaloriesCircular.setProgress(progress, true)
        
        tvProteinVal.text = "${proteinTotal}g"
        tvCarbsVal.text = "${carbsTotal}g"
        tvFatVal.text = "${fatTotal}g"
        
        progressProtein.setProgress((proteinTotal * 100 / 150).coerceAtMost(100), true)
        progressCarbs.setProgress((carbsTotal * 100 / 300).coerceAtMost(100), true)
        progressFat.setProgress((fatTotal * 100 / 70).coerceAtMost(100), true)
        
        // Burned calories from workout repo
        val activeMins = repo.getTodayActiveMinutes()
        val burned = activeMins * 8 // Estimation: 8 kcal per active minute
        tvBurnedVal.text = String.format("%,d", burned)
    }

    private fun updateWaterIntakeDisplay() {
        val count = repo.getWaterIntake()
        tvWaterCount.text = "$count / 2400 ml"
    }

    private fun showAddMealDialog(prefillData: NutritionData, prefillName: String, imageUri: String?) {
        val layout = LinearLayout(requireContext())
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val nameInput = EditText(requireContext())
        nameInput.hint = "Meal Name"
        nameInput.setText(prefillName)
        layout.addView(nameInput)

        val calInput = EditText(requireContext())
        calInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        calInput.hint = "Calories"
        calInput.setText(if (prefillData.calories > 0) prefillData.calories.toString() else "")
        layout.addView(calInput)
        
        val proteinInput = EditText(requireContext())
        proteinInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        proteinInput.hint = "Protein (g)"
        proteinInput.setText(if (prefillData.protein > 0) prefillData.protein.toString() else "")
        layout.addView(proteinInput)
        
        val carbsInput = EditText(requireContext())
        carbsInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        carbsInput.hint = "Carbs (g)"
        carbsInput.setText(if (prefillData.carbs > 0) prefillData.carbs.toString() else "")
        layout.addView(carbsInput)
        
        val fatInput = EditText(requireContext())
        fatInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        fatInput.hint = "Fat (g)"
        fatInput.setText(if (prefillData.fat > 0) prefillData.fat.toString() else "")
        layout.addView(fatInput)
        
        val typeInput = EditText(requireContext())
        typeInput.hint = "Type (Breakfast, Lunch, Dinner, Snack)"
        
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val suggestedType = when (currentHour) {
            in 5..10 -> "Breakfast"
            in 11..14 -> "Lunch"
            in 17..21 -> "Dinner"
            else -> "Snack"
        }
        typeInput.setText(suggestedType)
        layout.addView(typeInput)

        AlertDialog.Builder(requireContext())
            .setTitle(if (prefillName.isNotEmpty()) "Confirm Logged Meal" else "Log New Meal")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val calStr = calInput.text.toString().trim()
                val protStr = proteinInput.text.toString().trim()
                val carbStr = carbsInput.text.toString().trim()
                val fatStr = fatInput.text.toString().trim()
                val type = typeInput.text.toString().trim()

                if (name.isEmpty() || type.isEmpty()) {
                    Toast.makeText(requireContext(), "Name and Type cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val calories = calStr.toIntOrNull() ?: 0
                val protein = protStr.toIntOrNull() ?: 0
                val carbs = carbStr.toIntOrNull() ?: 0
                val fat = fatStr.toIntOrNull() ?: 0

                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val time = timeFormat.format(Date())
                val meal = Meal(name, calories, protein, carbs, fat, time, type, imageUri)
                repo.addMeal(meal)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
