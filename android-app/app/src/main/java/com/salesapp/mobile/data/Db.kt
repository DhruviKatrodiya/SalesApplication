package com.salesapp.mobile.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager

/**
 * Connection details for the SQL Server the app talks to directly.
 * Persisted encrypted on the device (see [Db.saveConfig]).
 */
data class DbConfig(
    val host: String,
    val port: Int = 1433,
    val database: String = "SalesAppDb",
    val user: String,
    val password: String,
) {
    /**
     * jTDS URL. jTDS is used instead of the official mssql-jdbc because the
     * Microsoft driver relies on JDBC internals that are missing on Android.
     * loginTimeout/socketTimeout keep the UI from hanging on an unreachable server.
     */
    fun url(): String =
        "jdbc:jtds:sqlserver://$host:$port/$database;loginTimeout=8;socketTimeout=30"
}

/**
 * Single entry point for all database access. Every call opens a fresh short-lived
 * connection on the IO dispatcher — simplest robust model for a mobile network where
 * a pooled socket would go stale. Business logic lives in the repositories, not here.
 */
object Db {

    private const val PREFS = "salesapp_secure"
    private const val K_HOST = "db_host"
    private const val K_PORT = "db_port"
    private const val K_NAME = "db_name"
    private const val K_USER = "db_user"
    private const val K_PASS = "db_pass"

    @Volatile private var config: DbConfig? = null
    private var driverLoaded = false

    fun isConfigured(): Boolean = config != null

    fun currentConfig(): DbConfig? = config

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx,
        PREFS,
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Load saved connection details (if any) into memory. Call once on app start. */
    fun loadConfig(ctx: Context) {
        val p = prefs(ctx)
        val host = p.getString(K_HOST, null) ?: return
        val user = p.getString(K_USER, null) ?: return
        config = DbConfig(
            host = host,
            port = p.getInt(K_PORT, 1433),
            database = p.getString(K_NAME, "SalesAppDb") ?: "SalesAppDb",
            user = user,
            password = p.getString(K_PASS, "") ?: "",
        )
    }

    fun saveConfig(ctx: Context, cfg: DbConfig) {
        prefs(ctx).edit()
            .putString(K_HOST, cfg.host)
            .putInt(K_PORT, cfg.port)
            .putString(K_NAME, cfg.database)
            .putString(K_USER, cfg.user)
            .putString(K_PASS, cfg.password)
            .apply()
        config = cfg
    }

    fun clearConfig(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        config = null
    }

    private fun ensureDriver() {
        if (!driverLoaded) {
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            driverLoaded = true
        }
    }

    private fun open(cfg: DbConfig): Connection {
        ensureDriver()
        return DriverManager.getConnection(cfg.url(), cfg.user, cfg.password)
    }

    /**
     * Run [block] with an open connection on the IO dispatcher, closing it afterwards.
     * All repository methods funnel through here.
     */
    suspend fun <T> withConnection(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        val cfg = config ?: error("Database is not configured")
        open(cfg).use { conn -> block(conn) }
    }

    /** Open a throwaway connection to validate the saved/entered settings. */
    suspend fun testConnection(cfg: DbConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            open(cfg).use { c ->
                c.createStatement().use { st -> st.executeQuery("SELECT 1").close() }
            }
        }
    }
}
