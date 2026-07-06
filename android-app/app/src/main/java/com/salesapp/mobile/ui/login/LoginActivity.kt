package com.salesapp.mobile.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.repo.AuthRepository
import com.salesapp.mobile.databinding.ActivityLoginBinding
import com.salesapp.mobile.ui.main.MainActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private val auth = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnLogin.setOnClickListener { attemptLogin() }
        b.tvForgot.setOnClickListener {
            android.widget.Toast.makeText(
                this,
                "This device stores data offline. Reset your password from Profile → Change password after signing in.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
        b.tvRegister.setOnClickListener {
            android.widget.Toast.makeText(
                this,
                "Use the seeded account: sales@salesapp.com / Sales@123",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Restore a remembered session and skip the login screen.
        if (!Session.isLoggedIn()) {
            Session.rememberedUserId(this)?.let { restoreSession(it) }
        }
    }

    private fun restoreSession(userId: Int) {
        lifecycleScope.launch {
            val user = runCatching { auth.findById(userId) }.getOrNull()
            if (user != null) { Session.signIn(this@LoginActivity, user); goToMain() }
        }
    }

    private fun attemptLogin() {
        val email = b.etEmail.text?.toString()?.trim().orEmpty()
        val password = b.etPassword.text?.toString().orEmpty()
        b.tvError.text = ""
        if (email.isEmpty() || password.isEmpty()) {
            b.tvError.text = "Enter your email and password."
            return
        }
        busy(true)
        lifecycleScope.launch {
            val result = runCatching { auth.login(email, password) }
            busy(false)
            result.fold(
                onSuccess = { user ->
                    if (user == null) b.tvError.text = "Invalid email or password."
                    else { Session.signIn(this@LoginActivity, user); goToMain() }
                },
                onFailure = { b.tvError.text = "Error: ${it.message}" },
            )
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun busy(on: Boolean) {
        b.progress.visibility = if (on) View.VISIBLE else View.GONE
        b.btnLogin.isEnabled = !on
    }
}
