package com.salesapp.mobile.ui.login

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.salesapp.mobile.data.repo.AuthRepository
import com.salesapp.mobile.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch

/** Create a new local salesperson account. Mirrors the website register screen. */
class RegisterActivity : AppCompatActivity() {

    private lateinit var b: ActivityRegisterBinding
    private val auth = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnRegister.setOnClickListener { attempt() }
        b.tvSignIn.setOnClickListener { finish() }
    }

    private fun attempt() {
        val fullName = b.etFullName.text?.toString()?.trim().orEmpty()
        val email = b.etEmail.text?.toString()?.trim().orEmpty()
        val phone = b.etPhone.text?.toString()?.trim().orEmpty()
        val password = b.etPassword.text?.toString().orEmpty()
        b.tvError.text = ""

        if (fullName.isEmpty()) { b.tvError.text = "Full name is required."; return }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            b.tvError.text = "Enter a valid email."; return
        }
        if (phone.isNotEmpty() && !phone.matches(Regex("^\\d{7,15}$"))) {
            b.tvError.text = "Enter a valid phone number (7–15 digits)."; return
        }
        if (password.length < 6) { b.tvError.text = "Password must be at least 6 characters."; return }

        busy(true)
        lifecycleScope.launch {
            val result = runCatching { auth.register(fullName, email, phone.ifEmpty { null }, password) }
                .getOrElse { Result.failure(it) }
            busy(false)
            result.fold(
                onSuccess = {
                    Toast.makeText(this@RegisterActivity, "Account created. Please sign in.", Toast.LENGTH_LONG).show()
                    finish()
                },
                onFailure = { b.tvError.text = it.message ?: "Registration failed." },
            )
        }
    }

    private fun busy(on: Boolean) {
        b.progress.visibility = if (on) View.VISIBLE else View.GONE
        b.btnRegister.isEnabled = !on
    }
}
