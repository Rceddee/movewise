package com.example.movewise.controller

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.movewise.AuthActivity
import com.example.movewise.R
import com.example.movewise.model.ChatRepository
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        view.findViewById<Button>(R.id.btn_recommendations).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, RecommendationsFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<Button>(R.id.btn_badges).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, BadgesFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<Button>(R.id.btn_logout).setOnClickListener {
            com.example.movewise.model.DataRepository.reset()
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireActivity(), AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        return view
    }
}
