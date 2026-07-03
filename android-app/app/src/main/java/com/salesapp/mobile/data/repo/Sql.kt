package com.salesapp.mobile.data.repo

import java.sql.Connection

/** Small SQLite helpers shared by the repositories. */
object Sql {
    /** The id of the row inserted by the previous INSERT on this connection. */
    fun lastInsertId(conn: Connection): Int =
        conn.prepareStatement("SELECT last_insert_rowid()").use { ps ->
            ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }

    /** Round money to 2 decimals to avoid floating-point drift in comparisons/totals. */
    fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
}
