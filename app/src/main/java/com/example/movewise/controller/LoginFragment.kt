package com.example.movewise.controller

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.movewise.AuthActivity
import com.example.movewise.MainActivity
import com.example.movewise.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException

class LoginFragment : Fragment() {
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)
        auth = FirebaseAuth.getInstance()

        val etEmail: EditText = view.findViewById(R.id.et_email)
        val etPass: EditText = view.findViewById(R.id.et_password)
        val btnLogin: Button = view.findViewById(R.id.btn_login)
        val tvSignUp: TextView = view.findViewById(R.id.tv_go_to_signup)
        val progressBar: ProgressBar? = view.findViewById(R.id.progress_bar)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            progressBar?.visibility = View.VISIBLE

            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    btnLogin.isEnabled = true
                    progressBar?.visibility = View.GONE

                    if (task.isSuccessful) {
                        Log.d("LoginFragment", "Login successful: ${auth.currentUser?.email}")
                        startActivity(Intent(requireContext(), MainActivity::class.java))
                        requireActivity().finish()
                    } else {
                        val errorCode = (task.exception as? FirebaseAuthException)?.errorCode ?: "unknown"
                        val errorMsg = task.exception?.message ?: "Unknown error"
                        Log.e("LoginFragment", "Login failed [$errorCode]: $errorMsg")

                        val userMessage = when (errorCode) {
                            "ERROR_USER_NOT_FOUND" -> "No account found with this email. Please sign up first."
                            "ERROR_WRONG_PASSWORD" -> "Incorrect password. Please try again."
                            "ERROR_INVALID_EMAIL" -> "Invalid email format."
                            "ERROR_USER_DISABLED" -> "This account has been disabled."
                            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again later."
                            "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Check your internet connection."
                            else -> "Login failed: $errorMsg"
                        }

                        AlertDialog.Builder(requireContext())
                            .setTitle("Login Error")
                            .setMessage(userMessage)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
        }

        tvSignUp.setOnClickListener {
            (requireActivity() as AuthActivity).showSignUp()
        }

        return view
    }
}
