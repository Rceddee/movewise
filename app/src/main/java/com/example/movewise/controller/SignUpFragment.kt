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

class SignUpFragment : Fragment() {
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_signup, container, false)
        auth = FirebaseAuth.getInstance()

        val etEmail: EditText = view.findViewById(R.id.et_signup_email)
        val etPass: EditText = view.findViewById(R.id.et_signup_password)
        val btnSignUp: Button = view.findViewById(R.id.btn_signup)
        val tvLogin: TextView = view.findViewById(R.id.tv_go_to_login)
        val progressBar: ProgressBar? = view.findViewById(R.id.progress_bar_signup)

        btnSignUp.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pass.length < 6) {
                Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSignUp.isEnabled = false
            progressBar?.visibility = View.VISIBLE

            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    btnSignUp.isEnabled = true
                    progressBar?.visibility = View.GONE

                    if (task.isSuccessful) {
                        Log.d("SignUpFragment", "Account created: ${auth.currentUser?.email}")
                        Toast.makeText(requireContext(), "Account Created! Welcome!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(requireContext(), MainActivity::class.java))
                        requireActivity().finish()
                    } else {
                        val errorCode = (task.exception as? FirebaseAuthException)?.errorCode ?: "unknown"
                        val errorMsg = task.exception?.message ?: "Unknown error"
                        Log.e("SignUpFragment", "Signup failed [$errorCode]: $errorMsg")

                        val userMessage = when (errorCode) {
                            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Try logging in."
                            "ERROR_INVALID_EMAIL" -> "Invalid email format."
                            "ERROR_WEAK_PASSWORD" -> "Password is too weak. Use at least 6 characters."
                            "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Check your internet connection."
                            "ERROR_OPERATION_NOT_ALLOWED" -> "Email/Password sign-up is not enabled. Please enable it in Firebase Console → Authentication → Sign-in Method."
                            else -> "Sign up failed: $errorMsg"
                        }

                        AlertDialog.Builder(requireContext())
                            .setTitle("Sign Up Error")
                            .setMessage(userMessage)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
        }

        tvLogin.setOnClickListener {
            (requireActivity() as AuthActivity).showLogin()
        }

        return view
    }
}
