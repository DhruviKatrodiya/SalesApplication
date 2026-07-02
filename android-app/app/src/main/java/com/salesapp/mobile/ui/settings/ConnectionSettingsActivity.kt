package com.salesapp.mobile.ui.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.DbConfig
import com.salesapp.mobile.databinding.ActivityConnectionSettingsBinding
import kotlinx.coroutines.launch

/** Lets the user enter and validate the SQL Server connection details. */
class ConnectionSettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivityConnectionSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityConnectionSettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        Db.currentConfig()?.let { cfg ->
            b.etHost.setText(cfg.host)
            b.etPort.setText(cfg.port.toString())
            b.etDatabase.setText(cfg.database)
            b.etUser.setText(cfg.user)
            b.etPassword.setText(cfg.password)
        }

        b.btnTest.setOnClickListener { readConfig()?.let { test(it) } }
        b.btnSave.setOnClickListener { readConfig()?.let { save(it) } }
    }

    private fun readConfig(): DbConfig? {
        val host = b.etHost.text?.toString()?.trim().orEmpty()
        val user = b.etUser.text?.toString()?.trim().orEmpty()
        if (host.isEmpty()) { b.tvStatus.text = "Host is required."; return null }
        if (user.isEmpty()) { b.tvStatus.text = "SQL login user is required."; return null }
        val port = b.etPort.text?.toString()?.trim()?.toIntOrNull() ?: 1433
        return DbConfig(
            host = host,
            port = port,
            database = b.etDatabase.text?.toString()?.trim().orEmpty().ifEmpty { "SalesAppDb" },
            user = user,
            password = b.etPassword.text?.toString().orEmpty(),
        )
    }

    private fun test(cfg: DbConfig) {
        busy(true, "Testing…")
        lifecycleScope.launch {
            val result = Db.testConnection(cfg)
            busy(false)
            b.tvStatus.text = result.fold(
                onSuccess = { "✓ Connected successfully." },
                onFailure = { "✗ ${it.message ?: "Connection failed."}" },
            )
        }
    }

    private fun save(cfg: DbConfig) {
        busy(true, "Verifying…")
        lifecycleScope.launch {
            val result = Db.testConnection(cfg)
            if (result.isSuccess) {
                Db.saveConfig(this@ConnectionSettingsActivity, cfg)
                busy(false)
                finish()
            } else {
                busy(false)
                b.tvStatus.text = "✗ ${result.exceptionOrNull()?.message ?: "Cannot connect — not saved."}"
            }
        }
    }

    private fun busy(on: Boolean, msg: String = "") {
        b.progress.visibility = if (on) View.VISIBLE else View.GONE
        b.btnTest.isEnabled = !on
        b.btnSave.isEnabled = !on
        if (msg.isNotEmpty()) b.tvStatus.text = msg
    }
}
