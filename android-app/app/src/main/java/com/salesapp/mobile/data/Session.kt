package com.salesapp.mobile.data

import android.content.Context
import com.salesapp.mobile.data.models.User

/**
 * Holds the signed-in user for the process lifetime and remembers the user id
 * across launches so the app doesn't force a re-login every cold start.
 */
object Session {
    private const val PREFS = "salesapp_session"
    private const val K_USER_ID = "user_id"

    @Volatile var currentUser: User? = null
        private set

    val userId: Int get() = currentUser?.id ?: error("No signed-in user")

    fun isLoggedIn(): Boolean = currentUser != null

    fun signIn(ctx: Context, user: User) {
        currentUser = user
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(K_USER_ID, user.id).apply()
    }

    fun signOut(ctx: Context) {
        currentUser = null
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** The remembered user id from a previous session, or null. */
    fun rememberedUserId(ctx: Context): Int? {
        val id = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(K_USER_ID, -1)
        return if (id > 0) id else null
    }
}
