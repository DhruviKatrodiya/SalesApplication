package com.salesapp.mobile.ui.login

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.salesapp.mobile.data.repo.AuthRepository
import com.salesapp.mobile.databinding.ActivityResetPasswordBinding
import kotlinx.coroutines.launch

/** Password-reset step 2: verify the OTP and set a new password. Mirrors the website reset screen. */
class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var b: ActivityResetPasswordBinding
    private val auth = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(b.root)
        intent.getStringExtra(EXTRA_EMAIL)?.let { b.etEmail.setText(it) }
        b.btnVerify.setOnClickListener { verify() }
        b.btnReset.setOnClickListener { reset() }
        b.tvBack.setOnClickListener { finish() }
    }

    private fun verify() {
        val email = b.etEmail.text?.toString()?.trim().orEmpty()
        val otp = b.etOtp.text?.toString()?.trim().orEmpty()
        b.tvError.text = ""
        if (!otp.matches(Regex("^\\d{6}$"))) { b.tvError.text = "OTP must be 6 digits."; return }
        busy(true)
        lifecycleScope.launch {
            val ok = runCatching { auth.verifyOtp(email, otp, consume = false) }.getOrDefault(false)
            busy(false)
            if (ok) Toast.makeText(this@ResetPasswordActivity, "OTP verified.", Toast.LENGTH_SHORT).show()
            else b.tvError.text = "Invalid or expired OTP."
        }
    }

    private fun reset() {
        val email = b.etEmail.text?.toString()?.trim().orEmpty()
        val otp = b.etOtp.text?.toString()?.trim().orEmpty()
        val newPw = b.etNew.text?.toString().orEmpty()
        val confirm = b.etConfirm.text?.toString().orEmpty()
        b.tvError.text = ""
        if (!otp.matches(Regex("^\\d{6}$"))) { b.tvError.text = "OTP must be 6 digits."; return }
        if (newPw.length < 6) { b.tvError.text = "At least 6 characters."; return }
        if (newPw != confirm) { b.tvError.text = "Passwords do not match."; return }

        busy(true)
        lifecycleScope.launch {
            val result = runCatching { auth.resetPassword(email, otp, newPw) }.getOrElse { Result.failure(it) }
            busy(false)
            result.fold(
                onSuccess = {
                    Toast.makeText(this@ResetPasswordActivity, "Password has been reset successfully.", Toast.LENGTH_LONG).show()
                    finish()
                },
                onFailure = { b.tvError.text = it.message ?: "Could not reset password." },
            )
        }
    }

    private fun busy(on: Boolean) {
        b.progress.visibility = if (on) View.VISIBLE else View.GONE
        b.btnReset.isEnabled = !on
        b.btnVerify.isEnabled = !on
    }

    companion object { const val EXTRA_EMAIL = "email" }
}
