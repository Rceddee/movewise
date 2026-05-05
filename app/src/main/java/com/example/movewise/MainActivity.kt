package com.example.movewise

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.movewise.controller.DashboardFragment
import com.example.movewise.controller.WorkoutFragment
import com.example.movewise.controller.NutritionFragment
import com.example.movewise.controller.ProgressFragment
import com.example.movewise.controller.ChatBotFragment
import com.example.movewise.controller.ChatPersonalizationFragment
import com.example.movewise.model.ChatRepository
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private val chatRepository = ChatRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            // Safety check: if user is not logged in, redirect to Auth
            val intent = android.content.Intent(this, AuthActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // Initialize DataRepository for local offline storage with unique user UID
        com.example.movewise.model.DataRepository.init(applicationContext, currentUser.uid)
        
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        
        // Set initial fragment
        loadFragment(DashboardFragment())

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> loadFragment(DashboardFragment())
                R.id.nav_workout -> loadFragment(WorkoutFragment())
                R.id.nav_nutrition -> loadFragment(NutritionFragment())
                R.id.nav_progress -> loadFragment(ProgressFragment())
                R.id.nav_chatbot -> loadFragment(ChatBotFragment())
            }
            true
        }
    }

    fun openPersonalization() {
        loadFragment(ChatPersonalizationFragment(chatRepository))
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }
}