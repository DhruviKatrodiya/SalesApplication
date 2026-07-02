package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.models.User

/** Profile read/update, mirroring ProfileController (image upload is omitted — no server/web root). */
class ProfileRepository {

    suspend fun me(): User? = Db.withConnection { conn ->
        conn.prepareStatement("SELECT Id, FullName, Email, Phone, ProfileImagePath FROM Users WHERE Id = ?").use { ps ->
            ps.setInt(1, Session.userId)
            ps.executeQuery().use {
                if (!it.next()) null
                else User(it.getInt("Id"), it.getString("FullName") ?: "", it.getString("Email") ?: "", it.getString("Phone"), it.getString("ProfileImagePath"))
            }
        }
    }

    /** Update profile; rejects an email already used by another account. */
    suspend fun update(fullName: String, email: String, phone: String?): Result<User> = Db.withConnection { conn ->
        val e = email.trim()
        if (e.isEmpty()) return@withConnection Result.failure(IllegalArgumentException("Email is required."))
        val taken = conn.prepareStatement("SELECT 1 FROM Users WHERE Id <> ? AND Email = ?").use { ps ->
            ps.setInt(1, Session.userId); ps.setString(2, e); ps.executeQuery().use { it.next() }
        }
        if (taken) return@withConnection Result.failure(IllegalStateException("That email address is already in use."))
        conn.prepareStatement("UPDATE Users SET FullName = ?, Email = ?, Phone = ? WHERE Id = ?").use { ps ->
            ps.setString(1, fullName.trim()); ps.setString(2, e); ps.setString(3, phone?.trim()); ps.setInt(4, Session.userId)
            ps.executeUpdate()
        }
        Result.success(User(Session.userId, fullName.trim(), e, phone?.trim(), Session.currentUser?.profileImagePath))
    }
}
