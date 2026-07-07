package com.salesapp.mobile.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.salesapp.mobile.data.repo.AuthRepository
import com.salesapp.mobile.databinding.ActivityForgotPasswordBinding
import kotlinx.coroutines.launch

/**
 * Password-reset step 1. Mirrors the website forgot-password flow: enter email → an OTP is created.
 * Offline there is no email server, so the generated code is shown on-screen and the user is taken
 * to the reset screen (equivalent to the website's "an OTP has been sent" + navigate to reset).
 */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var b: ActivityForgotPasswordBinding
    private val auth = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnSendOtp.setOnClickListener { send() }
        b.tvBack.setOnClickListener { finish() }
    }

    private fun send() {
        val email = b.etEmail.text?.toString()?.trim().orEmpty()
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) return
        busy(true)
        lifecycleScope.launch {
            val otp = runCatching { auth.generateOtp(email) }.getOrNull()
            busy(false)
            if (otp == null) {
                // Same non-enumerating message the website shows when the email doesn't exist.
                MaterialAlertDialogBuilder(this@ForgotPasswordActivity)
                    .setTitle("Check your email")
                    .setMessage("If the email exists, an OTP has been sent.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                MaterialAlertDialogBuilder(this@ForgotPasswordActivity)
                    .setTitle("Your OTP code")
                    .setMessage(
                        "This device is offline, so your one-time code can't be emailed. " +
                            "Use this code (valid 10 minutes):\n\n$otp"
                    )
                    .setCancelable(false)
                    .setPositiveButton("Continue") { _, _ -> goToReset(email) }
                    .show()
            }
        }
    }

    private fun goToReset(email: String) {
        startActivity(
            Intent(this, ResetPasswordActivity::class.java).putExtra(ResetPasswordActivity.EXTRA_EMAIL, email)
        )
        finish()
    }

    private fun busy(on: Boolean) {
        b.progress.visibility = if (on) View.VISIBLE else View.GONE
        b.btnSendOtp.isEnabled = !on
    }
}
