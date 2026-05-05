package com.example.movewise.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

class PoseGraphicOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val pointPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    
    private val linePaint = Paint().apply {
        color = Color.parseColor("#806366F1") // semi-transparent brand_royal
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    
    private var pose: Pose? = null
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    fun updatePose(pose: Pose, imageWidth: Int, imageHeight: Int) {
        this.pose = pose
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val currentPose = pose ?: return
        if (imageWidth == 0 || imageHeight == 0) return

        // Calculate scaling factors
        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight
        val scale = Math.max(scaleX, scaleY)

        // Calculate offsets to center the image (assuming center crop)
        val viewportWidth = imageWidth * scale
        val viewportHeight = imageHeight * scale
        val startX = (width - viewportWidth) / 2
        val startY = (height - viewportHeight) / 2

        fun translateX(x: Float): Float = startX + x * scale
        fun translateY(y: Float): Float = startY + y * scale
        
        // Draw lines between specific joints
        drawLine(canvas, currentPose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER, ::translateX, ::translateY)
        drawLine(canvas, currentPose, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP, ::translateX, ::translateY)
        
        // Left Arm
        drawLine(canvas, currentPose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, ::translateX, ::translateY)
        drawLine(canvas, currentPose, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST, ::translateX, ::translateY)
        
        // Right Arm
        drawLine(canvas, currentPose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, ::translateX, ::translateY)
        drawLine(canvas, currentPose, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST, ::translateX, ::translateY)
        
        // Torso
        drawLine(canvas, currentPose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP, ::translateX, ::translateY)
        drawLine(canvas, currentPose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP, ::translateX, ::translateY)
        
        // Left Leg
        drawLine(canvas, currentPose, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, ::translateX, ::translateY)
        drawLine(canvas, currentPose, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE, ::translateX, ::translateY)
        
        // Right Leg
        drawLine(canvas, currentPose, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, ::translateX, ::translateY)
        drawLine(canvas, currentPose, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE, ::translateX, ::translateY)

        // Draw points for all available landmarks
        for (landmark in currentPose.allPoseLandmarks) {
            // Only draw points with reasonable confidence
            if (landmark.inFrameLikelihood > 0.3f) {
                // Nose/eyes/mouth (0-10) get smaller dots, joints get larger ones
                val radius = if (landmark.landmarkType <= 10) 8f else 12f
                canvas.drawCircle(
                    translateX(landmark.position.x),
                    translateY(landmark.position.y),
                    radius,
                    pointPaint
                )
            }
        }
    }
    
    // Helper function to draw lines between landmarks if both have confidence
    private fun drawLine(
        canvas: Canvas,
        pose: Pose,
        startLandmarkType: Int,
        endLandmarkType: Int,
        translateX: (Float) -> Float,
        translateY: (Float) -> Float
    ) {
        val start = pose.getPoseLandmark(startLandmarkType)
        val end = pose.getPoseLandmark(endLandmarkType)
        
        if (start != null && end != null && start.inFrameLikelihood > 0.3f && end.inFrameLikelihood > 0.3f) {
            canvas.drawLine(
                translateX(start.position.x),
                translateY(start.position.y),
                translateX(end.position.x),
                translateY(end.position.y),
                linePaint
            )
        }
    }
}
