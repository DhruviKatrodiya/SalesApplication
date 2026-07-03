package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.models.DeliveryRoute
import com.salesapp.mobile.data.models.Paged
import java.sql.PreparedStatement

/** CRUD for delivery routes (local SQLite). Customers keep their route on delete. */
class RouteRepository {

    suspend fun list(
        search: String? = null,
        active: String = "active",
        page: Int = 1,
        pageSize: Int = 500,
    ): Paged<DeliveryRoute> = Db.withConnection { conn ->
        val uid = Session.userId
        val where = StringBuilder("WHERE r.UserId = ?")
        val args = mutableListOf<Any>(uid)
        if (active != "all") { where.append(" AND r.IsActive = ?"); args.add(if (active == "inactive") 0 else 1) }
        if (!search.isNullOrBlank()) { where.append(" AND r.Name LIKE ?"); args.add("%${search.trim()}%") }

        val total = conn.prepareStatement("SELECT COUNT(*) FROM Routes r $where").use { ps ->
            bind(ps, args); ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }
        val safePage = if (page < 1) 1 else page
        val sql = """
            SELECT r.Id, r.Name, r.Description, r.IsActive,
                   (SELECT COUNT(*) FROM Customers c WHERE c.RouteId = r.Id) AS CustCount
            FROM Routes r
            $where
            ORDER BY r.CreatedAt DESC, r.Id DESC
            LIMIT ?, ?
        """.trimIndent()
        val items = conn.prepareStatement(sql).use { ps ->
            val n = bind(ps, args); ps.setInt(n, (safePage - 1) * pageSize); ps.setInt(n + 1, pageSize)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        DeliveryRoute(
                            id = rs.getInt("Id"), name = rs.getString("Name") ?: "",
                            description = rs.getString("Description"), customerCount = rs.getInt("CustCount"),
                            isActive = rs.getBoolean("IsActive"),
                        )
                    )
                }
            }
        }
        Paged(items, total, safePage, pageSize)
    }

    suspend fun create(name: String, description: String?): Int = Db.withConnection { conn ->
        conn.prepareStatement(
            "INSERT INTO Routes (UserId, Name, Description, CreatedAt, IsActive) VALUES (?, ?, ?, datetime('now'), 1)"
        ).use { ps ->
            ps.setInt(1, Session.userId); ps.setString(2, name.trim()); ps.setString(3, description?.trim()); ps.executeUpdate()
        }
        Sql.lastInsertId(conn)
    }

    suspend fun update(id: Int, name: String, description: String?): Boolean = Db.withConnection { conn ->
        conn.prepareStatement("UPDATE Routes SET Name = ?, Description = ? WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setString(1, name.trim()); ps.setString(2, description?.trim()); ps.setInt(3, id); ps.setInt(4, Session.userId)
            ps.executeUpdate() > 0
        }
    }

    suspend fun deactivate(id: Int) = setActive(id, false)
    suspend fun activate(id: Int) = setActive(id, true)

    private suspend fun setActive(id: Int, active: Boolean): Boolean = Db.withConnection { conn ->
        conn.prepareStatement("UPDATE Routes SET IsActive = ? WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setBoolean(1, active); ps.setInt(2, id); ps.setInt(3, Session.userId); ps.executeUpdate() > 0
        }
    }

    private fun bind(ps: PreparedStatement, args: List<Any>): Int {
        args.forEachIndexed { i, a -> when (a) {
            is Int -> ps.setInt(i + 1, a); is String -> ps.setString(i + 1, a); else -> ps.setObject(i + 1, a)
        } }
        return args.size + 1
    }
}
