package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.models.User
import org.mindrot.jbcrypt.BCrypt

/**
 * Auth against the [Users] table. Mirrors the backend AuthController: look up by email,
 * verify the BCrypt PasswordHash. jBCrypt reads the $2a$ hashes produced by BCrypt.Net.
 */
class AuthRepository {

    /** @return the signed-in [User] on success, or null on bad credentials. */
    suspend fun login(email: String, password: String): User? = Db.withConnection { conn ->
        val sql = "SELECT Id, FullName, Email, PasswordHash, Phone, ProfileImagePath " +
            "FROM Users WHERE Email = ?"
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, email.trim())
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@withConnection null
                val hash = rs.getString("PasswordHash") ?: return@withConnection null
                if (!verify(password, hash)) return@withConnection null
                User(
                    id = rs.getInt("Id"),
                    fullName = rs.getString("FullName") ?: "",
                    email = rs.getString("Email") ?: "",
                    phone = rs.getString("Phone"),
                    profileImagePath = rs.getString("ProfileImagePath"),
                )
            }
        }
    }

    /**
     * Register a new local salesperson account (mirrors AuthController.Register). Does NOT sign in —
     * the caller sends the user to the login screen, exactly like the website.
     */
    suspend fun register(fullName: String, email: String, phone: String?, password: String): Result<User> =
        Db.withConnection { conn ->
            val e = email.trim()
            if (e.isEmpty() || password.isEmpty())
                return@withConnection Result.failure(IllegalArgumentException("Email and password are required."))
            if (password.length < 6)
                return@withConnection Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
            val taken = conn.prepareStatement("SELECT 1 FROM Users WHERE Email = ?").use { ps ->
                ps.setString(1, e); ps.executeQuery().use { it.next() }
            }
            if (taken)
                return@withConnection Result.failure(IllegalStateException("An account with this email already exists."))

            val name = fullName.trim().ifEmpty { e }
            val hash = BCrypt.hashpw(password, BCrypt.gensalt())
            conn.prepareStatement(
                "INSERT INTO Users (FullName, Email, PasswordHash, Phone, CreatedAt) VALUES (?, ?, ?, ?, datetime('now'))"
            ).use { ps ->
                ps.setString(1, name); ps.setString(2, e); ps.setString(3, hash); ps.setString(4, phone?.trim())
                ps.executeUpdate()
            }
            Result.success(User(Sql.lastInsertId(conn), name, e, phone?.trim(), null))
        }

    /**
     * Start a password reset: mirror OtpService.GenerateAsync — 6-digit OTP, previous unused codes
     * invalidated, hashed + stored with a 10-minute expiry. Returns the plaintext OTP when the email
     * belongs to an account (offline we surface it to the user in place of sending an email), else null.
     */
    suspend fun generateOtp(email: String): String? = Db.withConnection { conn ->
        val e = email.trim()
        val exists = conn.prepareStatement("SELECT 1 FROM Users WHERE Email = ?").use { ps ->
            ps.setString(1, e); ps.executeQuery().use { it.next() }
        }
        if (!exists) return@withConnection null
        val otp = (0 until 6).joinToString("") { (Math.random() * 10).toInt().toString() }
        conn.prepareStatement("UPDATE PasswordResetOtps SET IsUsed = 1 WHERE Email = ? AND IsUsed = 0").use { ps ->
            ps.setString(1, e); ps.executeUpdate()
        }
        conn.prepareStatement(
            "INSERT INTO PasswordResetOtps (Email, OtpHash, ExpiresAt, IsUsed, CreatedAt) " +
                "VALUES (?, ?, datetime('now','+10 minutes'), 0, datetime('now'))"
        ).use { ps ->
            ps.setString(1, e); ps.setString(2, BCrypt.hashpw(otp, BCrypt.gensalt()))
            ps.executeUpdate()
        }
        otp
    }

    /** Verify an OTP (latest unused, unexpired). Optionally consume it. Mirrors OtpService.VerifyAsync. */
    suspend fun verifyOtp(email: String, otp: String, consume: Boolean): Boolean = Db.withConnection { conn ->
        val e = email.trim()
        val row = conn.prepareStatement(
            "SELECT Id, OtpHash FROM PasswordResetOtps " +
                "WHERE Email = ? AND IsUsed = 0 AND ExpiresAt > datetime('now') ORDER BY CreatedAt DESC, Id DESC LIMIT 1"
        ).use { ps ->
            ps.setString(1, e)
            ps.executeQuery().use { if (it.next()) it.getInt("Id") to it.getString("OtpHash") else null }
        } ?: return@withConnection false
        if (!verify(otp, row.second)) return@withConnection false
        if (consume) conn.prepareStatement("UPDATE PasswordResetOtps SET IsUsed = 1 WHERE Id = ?").use { ps ->
            ps.setInt(1, row.first); ps.executeUpdate()
        }
        true
    }

    /** Reset a password using a valid OTP (verifies + consumes). Mirrors AuthController.ResetPassword. */
    suspend fun resetPassword(email: String, otp: String, newPassword: String): Result<Unit> {
        if (newPassword.length < 6)
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        if (!verifyOtp(email, otp, consume = true))
            return Result.failure(IllegalArgumentException("Invalid or expired OTP."))
        return Db.withConnection { conn ->
            val hash = BCrypt.hashpw(newPassword, BCrypt.gensalt())
            val n = conn.prepareStatement("UPDATE Users SET PasswordHash = ? WHERE Email = ?").use { ps ->
                ps.setString(1, hash); ps.setString(2, email.trim()); ps.executeUpdate()
            }
            if (n == 0) Result.failure(IllegalStateException("User not found."))
            else Result.success(Unit)
        }
    }

    /** Change the current user's password after re-checking the old one. */
    suspend fun changePassword(userId: Int, currentPassword: String, newPassword: String): Result<Unit> =
        Db.withConnection { conn ->
            val hash = conn.prepareStatement("SELECT PasswordHash FROM Users WHERE Id = ?").use { ps ->
                ps.setInt(1, userId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            } ?: return@withConnection Result.failure(IllegalStateException("User not found."))

            if (!verify(currentPassword, hash))
                return@withConnection Result.failure(IllegalArgumentException("Current password is incorrect."))

            val newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt())
            conn.prepareStatement("UPDATE Users SET PasswordHash = ? WHERE Id = ?").use { ps ->
                ps.setString(1, newHash)
                ps.setInt(2, userId)
                ps.executeUpdate()
            }
            Result.success(Unit)
        }

    /** Reload a user by id (used to restore a remembered session). */
    suspend fun findById(userId: Int): User? = Db.withConnection { conn ->
        conn.prepareStatement(
            "SELECT Id, FullName, Email, Phone, ProfileImagePath FROM Users WHERE Id = ?"
        ).use { ps ->
            ps.setInt(1, userId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) null
                else User(
                    id = rs.getInt("Id"),
                    fullName = rs.getString("FullName") ?: "",
                    email = rs.getString("Email") ?: "",
                    phone = rs.getString("Phone"),
                    profileImagePath = rs.getString("ProfileImagePath"),
                )
            }
        }
    }

    private fun verify(password: String, hash: String): Boolean = try {
        // Normalise a $2b$/$2y$ prefix to $2a$ which jBCrypt understands; the digest is identical.
        val normalized = when {
            hash.startsWith("$2b$") || hash.startsWith("$2y$") -> "\$2a\$" + hash.substring(4)
            else -> hash
        }
        BCrypt.checkpw(password, normalized)
    } catch (_: Exception) {
        false
    }
}
