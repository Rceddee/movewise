package com.example.movewise

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_splash)

        val logoContainer = findViewById<View>(R.id.logo_container)
        val tvAppName = findViewById<View>(R.id.tv_app_name)
        val tvTagline = findViewById<View>(R.id.tv_tagline)
        val progressBar = findViewById<View>(R.id.progressBar)

        // Logo: scale up + fade in with overshoot bounce
        val logoScaleX = ObjectAnimator.ofFloat(logoContainer, "scaleX", 0.4f, 1f).apply {
            duration = 900
            interpolator = OvershootInterpolator(1.5f)
        }
        val logoScaleY = ObjectAnimator.ofFloat(logoContainer, "scaleY", 0.4f, 1f).apply {
            duration = 900
            interpolator = OvershootInterpolator(1.5f)
        }
        val logoAlpha = ObjectAnimator.ofFloat(logoContainer, "alpha", 0f, 1f).apply {
            duration = 600
        }

        // App name: slide up + fade in
        val nameTranslateY = ObjectAnimator.ofFloat(tvAppName, "translationY", 60f, 0f).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            startDelay = 350
        }
        val nameAlpha = ObjectAnimator.ofFloat(tvAppName, "alpha", 0f, 1f).apply {
            duration = 500
            startDelay = 350
        }

        // Tagline: fade in
        val taglineAlpha = ObjectAnimator.ofFloat(tvTagline, "alpha", 0f, 1f).apply {
            duration = 600
            startDelay = 650
        }
        val taglineTranslateY = ObjectAnimator.ofFloat(tvTagline, "translationY", 30f, 0f).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            startDelay = 650
        }

        // Progress bar: fade in
        val progressAlpha = ObjectAnimator.ofFloat(progressBar, "alpha", 0f, 1f).apply {
            duration = 500
            startDelay = 900
        }

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(
            logoScaleX, logoScaleY, logoAlpha,
            nameTranslateY, nameAlpha,
            taglineAlpha, taglineTranslateY,
            progressAlpha
        )
        animatorSet.start()

        // Navigate after animations + brief pause
        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                startActivity(Intent(this, AuthActivity::class.java))
            }
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2500)
    }
}
