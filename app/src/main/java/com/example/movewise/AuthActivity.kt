package com.example.movewise

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.movewise.controller.LoginFragment
import com.example.movewise.controller.SignUpFragment

class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        if (savedInstanceState == null) {
            showLogin()
        }
    }

    fun showLogin() {
        replaceFragment(LoginFragment())
    }

    fun showSignUp() {
        replaceFragment(SignUpFragment())
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.auth_container, fragment)
            .commit()
    }
}
