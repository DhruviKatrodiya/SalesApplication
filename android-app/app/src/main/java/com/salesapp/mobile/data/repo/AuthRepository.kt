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
