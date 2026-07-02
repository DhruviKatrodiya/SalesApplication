package com.salesapp.mobile.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.repo.AuthRepository
import com.salesapp.mobile.databinding.ActivityLoginBinding
import com.salesapp.mobile.ui.main.MainActivity
import com.salesapp.mobile.ui.settings.ConnectionSettingsActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private val auth = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnConnection.setOnClickListener {
            startActivity(Intent(this, ConnectionSettingsActivity::class.java))
        }
        b.btnLogin.setOnClickListener { attemptLogin() }
    }

    override fun onResume() {
        super.onResume()
        // If the DB is set up and we have a remembered session, restore it and skip login.
        if (Db.isConfigured() && !Session.isLoggedIn()) {
            Session.rememberedUserId(this)?.let { restoreSession(it) }
        }
    }

    private fun restoreSession(userId: Int) {
        lifecycleScope.launch {
            val user = runCatching { auth.findById(userId) }.getOrNull()
            if (user != null) {
                Session.signIn(this@LoginActivity, user)
                goToMain()
            }
        }
    }

    private fun attemptLogin() {
        val email = b.etEmail.text?.toString()?.trim().orEmpty()
        val password = b.etPassword.text?.toString().orEmpty()
        b.tvError.text = ""

        if (!Db.isConfigured()) {
            b.tvError.text = "Set up the SQL Server connection first."
            startActivity(Intent(this, ConnectionSettingsActivity::class.java))
            return
        }
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
                    if (user == null) {
                        b.tvError.text = "Invalid email or password."
                    } else {
                        Session.signIn(this@LoginActivity, user)
                        goToMain()
                    }
                },
                onFailure = { b.tvError.text = "Connection error: ${it.message}" },
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
