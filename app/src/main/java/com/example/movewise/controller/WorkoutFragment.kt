package com.example.movewise.controller

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movewise.R
import com.example.movewise.model.DataRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class WorkoutFragment : Fragment(), TextToSpeech.OnInitListener {

    // ── Views ──────────────────────────────────────────────────────────────────
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var poseOverlay: PoseGraphicOverlay
    private lateinit var tvFeedback: TextView
    private lateinit var overlayView: View
    private var tts: TextToSpeech? = null
    private var lastSpokenExercise: String = ""
    private lateinit var tvRepsCount: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvDetectedExercise: TextView
    private lateinit var btnPauseResume: Button
    private lateinit var cardFormScore: View
    private lateinit var tvFormScore: TextView
    private val repo by lazy { DataRepository.getInstance() }

    // ── Tracking State ─────────────────────────────────────────────────────────
    private var repCount = 0
    private var isUpPosition = false          // generic up/down phase marker
    private var cameraFacing = CameraSelector.LENS_FACING_BACK
    private var isTracking = false
    private var elapsedSeconds = 0L
    private var currentFormScore = 100
    private var plankStartTime = 0L
    private var lastHoldAnnounced = -1L

    // ── Timer ──────────────────────────────────────────────────────────────────
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            elapsedSeconds++
            val m = elapsedSeconds / 60; val s = elapsedSeconds % 60
            tvTimer.text = "%02d:%02d".format(m, s)
            timerHandler.postDelayed(this, 1_000)
        }
    }

    // ── Exercise Detection ─────────────────────────────────────────────────────
    private var detectedExercise = "Get in frame…"
    private val voteBuffer = ArrayDeque<String>(40)
    private var consecutiveUnknown = 0          // hide detection chip when no body in frame

    // ── Per-exercise angle thresholds ──────────────────────────────────────────
    // (down-angle, up-angle): rep counted when angle crosses up→down→up
    private val SQUAT_DOWN   = 90f;  private val SQUAT_UP   = 165f
    private val CURL_DOWN    = 150f; private val CURL_UP    = 50f    // flex/extend
    private val PUSHUP_DOWN  = 90f;  private val PUSHUP_UP  = 160f
    private val PRESS_DOWN   = 90f;  private val PRESS_UP   = 165f
    private val LUNGE_DOWN   = 100f; private val LUNGE_UP   = 160f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_workout, container, false)

        previewView          = view.findViewById(R.id.previewView)
        poseOverlay          = view.findViewById(R.id.pose_overlay)
        tvFeedback           = view.findViewById(R.id.tv_ai_feedback)
        tvRepsCount          = view.findViewById(R.id.tv_reps_count)
        tvTimer              = view.findViewById(R.id.tv_timer)
        tvDetectedExercise   = view.findViewById(R.id.tv_detected_exercise)
        btnPauseResume       = view.findViewById(R.id.btn_pause_resume)
        cardFormScore        = view.findViewById(R.id.card_form_score)
        tvFormScore          = view.findViewById(R.id.tv_form_score)

        tts = TextToSpeech(requireContext(), this)

        cameraExecutor = Executors.newSingleThreadExecutor()

        view.findViewById<ImageButton>(R.id.btn_flip_camera).setOnClickListener {
            cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        }

        btnPauseResume.setOnClickListener {
            if (isTracking) pauseTracking() else resumeTracking()
        }

        view.findViewById<Button>(R.id.btn_reset_reps).setOnClickListener {
            repCount = 0; isUpPosition = false
            tvRepsCount.text = "0"; voteBuffer.clear()
            Toast.makeText(context, "Reps reset!", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btn_finish_workout).setOnClickListener {
            showFinishWorkoutDialog()
        }

        if (allPermissionsGranted()) startCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS)

        return view
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tts?.stop()
        tts?.shutdown()
        timerHandler.removeCallbacks(timerRunnable)
        cameraExecutor.shutdown()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Timer helpers
    // ──────────────────────────────────────────────────────────────────────────
    private fun resumeTracking() {
        isTracking = true
        btnPauseResume.text = "⏸  Pause"
        tvFeedback.text = "Tracking — get in frame!"
        timerHandler.post(timerRunnable)
    }

    private fun pauseTracking() {
        isTracking = false
        btnPauseResume.text = "▶  Resume"
        tvFeedback.text = "Paused"
        timerHandler.removeCallbacks(timerRunnable)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Finish dialog — auto fills type, duration, reps
    // ──────────────────────────────────────────────────────────────────────────
    private fun showFinishWorkoutDialog() {
        val wasTracking = isTracking
        if (wasTracking) pauseTracking()
        val mins = (elapsedSeconds / 60).coerceAtLeast(1).toInt()
        val autoType = detectedExercise.takeIf { it != "Get in frame…" && it != "Unknown" }
            ?: "General Workout"

        AlertDialog.Builder(requireContext())
            .setTitle("Finish Workout ✅")
            .setMessage(
                "🏋️  $autoType\n" +
                "⏱  $mins minutes\n" +
                "🔁  $repCount reps\n\nSave this session?"
            )
            .setPositiveButton("Save & Finish") { _, _ ->
                repo.addWorkout(autoType, mins, repCount)
                Toast.makeText(requireContext(),
                    "Saved: $autoType · $mins min · $repCount reps 🎉",
                    Toast.LENGTH_LONG).show()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, DashboardFragment()).commit()
            }
            .setNeutralButton("Edit type") { _, _ ->
                val input = android.widget.EditText(requireContext()).also { it.setText(autoType) }
                AlertDialog.Builder(requireContext())
                    .setTitle("Workout type")
                    .setView(input)
                    .setPositiveButton("Save") { _, _ ->
                        val t = input.text.toString().ifBlank { "General Workout" }
                        repo.addWorkout(t, mins)
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.container, DashboardFragment()).commit()
                    }.show()
            }
            .setNegativeButton("Keep Going") { _, _ ->
                if (wasTracking) resumeTracking()
            }.show()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Camera setup — uses ACCURATE detector for better landmark positions
    // ──────────────────────────────────────────────────────────────────────────
    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val context = context ?: return
        val future: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(context)

        future.addListener({
            if (!isAdded) return@addListener
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()

            // ▸ Use ACCURATE mode for full-body + reliable joint positions
            val opts = AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build()
            val detector = PoseDetection.getClient(opts)

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { proxy ->
                val img = proxy.image
                if (img != null && isTracking) {
                    val input = InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees)
                    
                    val isRotation90or270 = proxy.imageInfo.rotationDegrees == 90 || proxy.imageInfo.rotationDegrees == 270
                    val imageWidth = if (isRotation90or270) img.height else img.width
                    val imageHeight = if (isRotation90or270) img.width else img.height

                    detector.process(input)
                        .addOnSuccessListener { pose -> processPose(pose, imageWidth, imageHeight) }
                        .addOnFailureListener { Log.e(TAG, "Pose failed", it) }
                        .addOnCompleteListener { proxy.close() }
                } else {
                    proxy.close()
                }
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(viewLifecycleOwner, selector, preview, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Bind failed", e)
                if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
                    cameraFacing = CameraSelector.LENS_FACING_BACK; startCamera()
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ANGLE UTILITY  — returns the angle at vertex B, formed by A-B-C
    // ──────────────────────────────────────────────────────────────────────────
    private fun updateFormScore(score: Int) {
        currentFormScore = score
        activity?.runOnUiThread {
            cardFormScore.visibility = View.VISIBLE
            tvFormScore.text = "Form: $score%"
            
            // Color feedback
            val color = when {
                score >= 80 -> "#CC10B981" // Success Green
                score >= 50 -> "#CCF59E0B" // Warning Orange
                else -> "#CCEF4444"        // Danger Red
            }
            (cardFormScore as? com.google.android.material.card.MaterialCardView)?.setCardBackgroundColor(android.graphics.Color.parseColor(color))
        }
    }

    private fun angle(
        a: PoseLandmark, b: PoseLandmark, c: PoseLandmark
    ): Float {
        val ax = a.position.x - b.position.x
        val ay = a.position.y - b.position.y
        val cx = c.position.x - b.position.x
        val cy = c.position.y - b.position.y
        val dot   = ax * cx + ay * cy
        val magA  = sqrt((ax * ax + ay * ay).toDouble())
        val magC  = sqrt((cx * cx + cy * cy).toDouble())
        if (magA == 0.0 || magC == 0.0) return 0f
        val cos   = (dot / (magA * magC)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(Math.acos(cos)).toFloat()
    }

    // Convenience: get landmark only if confidence ≥ threshold
    private fun Pose.lm(id: Int, minConf: Float = 0.5f): PoseLandmark? {
        val lm = getPoseLandmark(id) ?: return null
        return if (lm.inFrameLikelihood >= minConf) lm else null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // EXERCISE CLASSIFIER
    // Uses angle + position heuristics for 6 exercises
    // ──────────────────────────────────────────────────────────────────────────
    private fun classifyExercise(pose: Pose): String {
        val ls = pose.lm(PoseLandmark.LEFT_SHOULDER)   ?: return "Unknown"
        val rs = pose.lm(PoseLandmark.RIGHT_SHOULDER)  ?: return "Unknown"
        val lh = pose.lm(PoseLandmark.LEFT_HIP)        ?: return "Unknown"
        val rh = pose.lm(PoseLandmark.RIGHT_HIP)       ?: return "Unknown"
        val lw = pose.lm(PoseLandmark.LEFT_WRIST, 0.4f)
        val rw = pose.lm(PoseLandmark.RIGHT_WRIST, 0.4f)
        val lk = pose.lm(PoseLandmark.LEFT_KNEE,  0.4f)
        val rk = pose.lm(PoseLandmark.RIGHT_KNEE,  0.4f)
        val la = pose.lm(PoseLandmark.LEFT_ANKLE,  0.4f)
        val ra = pose.lm(PoseLandmark.RIGHT_ANKLE,  0.4f)
        val le = pose.lm(PoseLandmark.LEFT_ELBOW,  0.4f)
        val re = pose.lm(PoseLandmark.RIGHT_ELBOW,  0.4f)

        val shoulderW  = abs(ls.position.x - rs.position.x)
        val hipMidY    = (lh.position.y + rh.position.y) / 2f
        val shoulderMidY = (ls.position.y + rs.position.y) / 2f
        val hipMidX    = (lh.position.x + rh.position.x) / 2f
        val shoulderMidX = (ls.position.x + rs.position.x) / 2f

        // ── Push-up: torso roughly horizontal ─────────────────────────────────
        if (le != null && re != null) {
            val torsoVert  = abs(hipMidY - shoulderMidY)
            val torsoHoriz = abs(hipMidX - shoulderMidX)
            if (torsoHoriz > torsoVert * 0.6f) return "Push-ups"
        }

        // ── Squat: knees bent significantly + hips low ─────────────────────────
        if (lk != null && rk != null && la != null && ra != null) {
            val kneeHipAngleL = angle(lh, lk, la)
            val kneeHipAngleR = angle(rh, rk, ra)
            val avgKneeAngle = (kneeHipAngleL + kneeHipAngleR) / 2f
            if (avgKneeAngle < 155f) return "Squats"
        }

        // ── Lunge: one knee bent, the other extended ───────────────────────────
        if (lk != null && rk != null && la != null && ra != null) {
            val leftKneeAngle  = angle(lh, lk, la)
            val rightKneeAngle = angle(rh, rk, ra)
            val diff = abs(leftKneeAngle - rightKneeAngle)
            if (diff > 35f && (leftKneeAngle < 140f || rightKneeAngle < 140f))
                return "Lunges"
        }

        // Wrist-based exercises need wrists
        if (lw == null && rw == null) return "Unknown"
        val wMidY = ((lw?.position?.y ?: 0f) + (rw?.position?.y ?: 0f)) / 2f
        val wSpread = abs((lw?.position?.x ?: 0f) - (rw?.position?.x ?: 0f))

        // ── Jumping Jacks: wrists above shoulders AND feet wide ────────────────
        if (wMidY < shoulderMidY && wSpread > shoulderW * 1.2f) {
            if (la != null && ra != null) {
                val ankleSpread = abs(la.position.x - ra.position.x)
                if (ankleSpread > shoulderW * 0.8f) return "Jumping Jacks"
            }
        }

        // ── Overhead Press: wrists above shoulders, arms close ─────────────────
        if (wMidY < shoulderMidY && wSpread < shoulderW * 1.0f)
            return "Overhead Press"

        // ── Lateral Raise: wrists ≈ shoulder height, wide spread ───────────────
        if (abs(wMidY - shoulderMidY) < shoulderW * 0.4f
            && wSpread > shoulderW * 1.4f)
            return "Lateral Raises"

        // ── Bicep Curl: elbows bent, wrists near chest ──────────────────────────
        if (le != null && re != null) {
            val curlL = angle(ls, le, lw ?: le)
            val curlR = angle(rs, re, rw ?: re)
            if ((curlL + curlR) / 2f < 110f) return "Bicep Curls"
        }

        // ── Plank: body horizontal, hip straight ────────────────────────────
        if (ls != null && lh != null && lk != null) {
            val slope = abs(ls.position.y - lh.position.y) / abs(ls.position.x - lh.position.x + 0.1f)
            val bodyAngle = angle(ls, lh, lk)
            if (slope < 0.5f && bodyAngle > 150f) return "Planks"
        }

        return "General Workout"
    }

    // ──────────────────────────────────────────────────────────────────────────
    // REP COUNTING — angle-based per exercise
    // ──────────────────────────────────────────────────────────────────────────
    private fun countRep(pose: Pose, exercise: String): Pair<Boolean, String> {
        // returns Pair(countedARep, feedbackText)
        return when (exercise) {

            "Squats" -> {
                val lh = pose.lm(PoseLandmark.LEFT_HIP)
                val lk = pose.lm(PoseLandmark.LEFT_KNEE)
                val la = pose.lm(PoseLandmark.LEFT_ANKLE)
                if (lh == null || lk == null || la == null)
                    return false to "Show full legs in frame"
                val a = angle(lh, lk, la)
                
                // Form Score for Squats (depth)
                if (a < 180f) {
                    val score = ((170f - a) / (170f - 70f) * 100).toInt().coerceIn(0, 100)
                    updateFormScore(score)
                }

                if (a < SQUAT_DOWN && !isUpPosition) {
                    isUpPosition = true
                    false to "⬆ Stand up to complete rep! (angle: ${a.toInt()}°)"
                } else if (a > SQUAT_UP && isUpPosition) {
                    isUpPosition = false
                    true to "🔥 Squat rep counted! (Form: $currentFormScore%)"
                } else {
                    false to if (isUpPosition) "Go lower! (${a.toInt()}°)" else "Bend knees to squat (${a.toInt()}°)"
                }
            }

            "Push-ups" -> {
                val ls = pose.lm(PoseLandmark.LEFT_SHOULDER)
                val le = pose.lm(PoseLandmark.LEFT_ELBOW)
                val lw = pose.lm(PoseLandmark.LEFT_WRIST)
                if (ls == null || le == null || lw == null)
                    return false to "Show arms in frame"
                val a = angle(ls, le, lw)

                // Form Score for Push-ups (chest depth)
                val score = ((160f - a) / (160f - 60f) * 100).toInt().coerceIn(0, 100)
                updateFormScore(score)

                if (a < PUSHUP_DOWN && !isUpPosition) {
                    isUpPosition = true
                    false to "⬆ Push up! (${a.toInt()}°)"
                } else if (a > PUSHUP_UP && isUpPosition) {
                    isUpPosition = false
                    true to "🔥 Push-up rep counted! (Form: $currentFormScore%)"
                } else {
                    false to if (isUpPosition) "Great, lower yourself (${a.toInt()}°)" else "Lower chest to ground (${a.toInt()}°)"
                }
            }

            "Bicep Curls" -> {
                val ls = pose.lm(PoseLandmark.LEFT_SHOULDER)
                val le = pose.lm(PoseLandmark.LEFT_ELBOW)
                val lw = pose.lm(PoseLandmark.LEFT_WRIST)
                if (ls == null || le == null || lw == null)
                    return false to "Show arms in frame"
                val a = angle(ls, le, lw)
                if (a < CURL_UP && !isUpPosition) {
                    isUpPosition = true
                    false to "⬆ Extend arm to complete (${a.toInt()}°)"
                } else if (a > CURL_DOWN && isUpPosition) {
                    isUpPosition = false
                    true to "🔥 Curl rep counted!"
                } else {
                    false to if (!isUpPosition) "Curl it up! (${a.toInt()}°)" else "Lower the weight (${a.toInt()}°)"
                }
            }

            "Overhead Press" -> {
                val ls = pose.lm(PoseLandmark.LEFT_SHOULDER)
                val le = pose.lm(PoseLandmark.LEFT_ELBOW)
                val lw = pose.lm(PoseLandmark.LEFT_WRIST)
                if (ls == null || le == null || lw == null)
                    return false to "Show arms in frame"
                val a = angle(ls, le, lw)
                if (a < PRESS_DOWN && !isUpPosition) {
                    isUpPosition = true
                    false to "⬆ Press overhead! (${a.toInt()}°)"
                } else if (a > PRESS_UP && isUpPosition) {
                    isUpPosition = false
                    true to "🔥 Press rep counted!"
                } else {
                    false to "Keep pressing (${a.toInt()}°)"
                }
            }

            "Lunges" -> {
                val lh = pose.lm(PoseLandmark.LEFT_HIP)
                val lk = pose.lm(PoseLandmark.LEFT_KNEE)
                val la = pose.lm(PoseLandmark.LEFT_ANKLE)
                if (lh == null || lk == null || la == null)
                    return false to "Show full body"
                val a = angle(lh, lk, la)
                if (a < LUNGE_DOWN && !isUpPosition) {
                    isUpPosition = true
                    false to "⬆ Rise up to complete (${a.toInt()}°)"
                } else if (a > LUNGE_UP && isUpPosition) {
                    isUpPosition = false; true to "🔥 Lunge rep counted!"
                } else {
                    false to if (!isUpPosition) "Step forward and lower (${a.toInt()}°)" else "Stand back up (${a.toInt()}°)"
                }
            }

            "Jumping Jacks", "Lateral Raises" -> {
                // Use wrist height relative to shoulder
                val ls = pose.lm(PoseLandmark.LEFT_SHOULDER) ?: return false to "Stand in frame"
                val lw = pose.lm(PoseLandmark.LEFT_WRIST, 0.3f) ?: return false to "Show arms"
                val rw = pose.lm(PoseLandmark.RIGHT_WRIST, 0.3f) ?: return false to "Show arms"
                val wMidY = (lw.position.y + rw.position.y) / 2f
                val isUp = wMidY < ls.position.y
                if (isUp && !isUpPosition) {
                    isUpPosition = true
                    false to "👍 Arms up! Now bring them down."
                } else if (!isUp && isUpPosition) {
                    isUpPosition = false; true to "🔥 Rep counted! Keep the pace."
                } else {
                    false to if (!isUpPosition) "Raise arms up!" else "Lower arms down!"
                }
            }

            "Planks" -> {
                if (plankStartTime == 0L) plankStartTime = System.currentTimeMillis()
                val holdTime = (System.currentTimeMillis() - plankStartTime) / 1000
                
                val ls = pose.lm(PoseLandmark.LEFT_SHOULDER)
                val lh = pose.lm(PoseLandmark.LEFT_HIP)
                val lk = pose.lm(PoseLandmark.LEFT_KNEE)
                if (ls != null && lh != null && lk != null) {
                    val bodyAngle = angle(ls, lh, lk)
                    val score = ((bodyAngle - 140f) / (180f - 140f) * 100).toInt().coerceIn(0, 100)
                    updateFormScore(score)
                    
                    if (holdTime > 0 && holdTime % 10 == 0L && holdTime != lastHoldAnnounced) {
                        lastHoldAnnounced = holdTime
                        speak("$holdTime seconds plank held")
                    }
                }
                
                false to "⏱ Plank hold: $holdTime sec (Form: $currentFormScore%)"
            }

            else -> {
                // Generic: wrist above shoulder counts
                val ls = pose.lm(PoseLandmark.LEFT_SHOULDER) ?: return false to "Get in frame"
                val rs = pose.lm(PoseLandmark.RIGHT_SHOULDER) ?: return false to "Get in frame"
                val lw = pose.lm(PoseLandmark.LEFT_WRIST, 0.3f) ?: return false to "Show wrists"
                val rw = pose.lm(PoseLandmark.RIGHT_WRIST, 0.3f) ?: return false to "Show wrists"
                val up = lw.position.y < ls.position.y && rw.position.y < rs.position.y
                if (up && !isUpPosition) {
                    isUpPosition = true; false to "👍 Good! Now lower down."
                } else if (!up && isUpPosition) {
                    isUpPosition = false; true to "🔥 Rep ${repCount + 1}!"
                } else false to "Move arms up and down to count reps"
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MAIN POSE PROCESSING
    // ──────────────────────────────────────────────────────────────────────────
    private fun processPose(pose: Pose, imageWidth: Int, imageHeight: Int) {
        // Draw the skeleton overlay
        activity?.runOnUiThread {
            poseOverlay.updatePose(pose, imageWidth, imageHeight)
        }
        
        // Check body visibility
        val ls = pose.lm(PoseLandmark.LEFT_SHOULDER, 0.4f)
        val rs = pose.lm(PoseLandmark.RIGHT_SHOULDER, 0.4f)

        if (ls == null || rs == null) {
            consecutiveUnknown++
            if (consecutiveUnknown > 15) {
                activity?.runOnUiThread {
                    tvFeedback.text = "📷 Stand back — ensure full upper body is visible"
                    tvDetectedExercise.text = "🏋️ Detecting…"
                }
            }
            return
        }
        consecutiveUnknown = 0

        // Classify with rolling vote
        val vote = classifyExercise(pose)
        if (voteBuffer.size >= 40) voteBuffer.removeFirst()
        voteBuffer.addLast(vote)
        val winner = voteBuffer.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "Unknown"

        if (winner != "Unknown" && winner != detectedExercise) {
            detectedExercise = winner
            isUpPosition = false // Reset rep phase on exercise switch
            if (winner != "Planks") {
                plankStartTime = 0L
                lastHoldAnnounced = -1L
            }
            activity?.runOnUiThread { cardFormScore.visibility = View.GONE }
        }

        // Count reps for the winning exercise
        val (gotRep, feedback) = countRep(pose, winner)
        if (gotRep) repCount++

        activity?.runOnUiThread {
            tvDetectedExercise.text = "🏋️ $detectedExercise"
            tvRepsCount.text = repCount.toString()
            if (gotRep) {
                // Coach Logic: Dynamic randomized motivational feedback
                val coachingMsg = when {
                    repCount % 10 == 0 -> listOf(
                        "$repCount reps! You're an absolute machine!",
                        "Ten more in the bank! Incredible work!",
                        "Boom! $repCount! Keep pushing those limits!"
                    ).random()
                    repCount % 5 == 0 -> listOf(
                        "$repCount. Halfway there, don't stop now!",
                        "Five more done. Keep that energy up!",
                        "Looking strong at $repCount! Breathe through it!"
                    ).random()
                    currentFormScore > 95 -> listOf(
                        "Textbook form on rep $repCount!",
                        "Rep $repCount. Perfect execution!",
                        "Flawless! That's how you do it."
                    ).random()
                    else -> "Rep $repCount"
                }
                speak(coachingMsg)
                tvFeedback.text = feedback
            }
            else if (feedback.isNotBlank()) tvFeedback.text = feedback
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) startCamera()
            else Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "WorkoutCamera"
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}
