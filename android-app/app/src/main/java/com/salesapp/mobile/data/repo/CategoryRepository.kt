package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.models.Category
import com.salesapp.mobile.data.models.Paged

/**
 * CRUD for [Category], mirroring the backend CategoriesController:
 *  - everything scoped to the signed-in user (UserId),
 *  - "delete" is a soft delete (IsActive = 0), with a separate activate,
 *  - list is paginated, name-searchable, and carries the sub-category count,
 *  - ordered by CreatedAt desc, then Id desc.
 */
class CategoryRepository {

    /** active: "active" (default) | "inactive" | "all". */
    suspend fun list(
        search: String? = null,
        active: String = "active",
        page: Int = 1,
        pageSize: Int = 20,
    ): Paged<Category> = Db.withConnection { conn ->
        val uid = Session.userId
        val where = StringBuilder("WHERE c.UserId = ?")
        val args = mutableListOf<Any>(uid)
        if (active != "all") {
            where.append(" AND c.IsActive = ?")
            args.add(if (active == "inactive") 0 else 1)
        }
        if (!search.isNullOrBlank()) {
            where.append(" AND c.Name LIKE ?")
            args.add("%${search.trim()}%")
        }

        val total = conn.prepareStatement("SELECT COUNT(*) FROM Categories c $where").use { ps ->
            bind(ps, args)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }

        val safePage = if (page < 1) 1 else page
        val sql = """
            SELECT c.Id, c.Name, c.Description, c.IsActive,
                   (SELECT COUNT(*) FROM SubCategories sc WHERE sc.CategoryId = c.Id) AS SubCount
            FROM Categories c
            $where
            ORDER BY c.CreatedAt DESC, c.Id DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """.trimIndent()

        val items = conn.prepareStatement(sql).use { ps ->
            val n = bind(ps, args)
            ps.setInt(n, (safePage - 1) * pageSize)
            ps.setInt(n + 1, pageSize)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        Category(
                            id = rs.getInt("Id"),
                            name = rs.getString("Name") ?: "",
                            description = rs.getString("Description"),
                            subCategoryCount = rs.getInt("SubCount"),
                            isActive = rs.getBoolean("IsActive"),
                        )
                    )
                }
            }
        }
        Paged(items, total, safePage, pageSize)
    }

    suspend fun create(name: String, description: String?): Int = Db.withConnection { conn ->
        val sql = "INSERT INTO Categories (UserId, Name, Description, CreatedAt, IsActive) " +
            "OUTPUT INSERTED.Id VALUES (?, ?, ?, SYSUTCDATETIME(), 1)"
        conn.prepareStatement(sql).use { ps ->
            ps.setInt(1, Session.userId)
            ps.setString(2, name.trim())
            ps.setString(3, description?.trim())
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    suspend fun update(id: Int, name: String, description: String?): Boolean = Db.withConnection { conn ->
        conn.prepareStatement(
            "UPDATE Categories SET Name = ?, Description = ? WHERE Id = ? AND UserId = ?"
        ).use { ps ->
            ps.setString(1, name.trim())
            ps.setString(2, description?.trim())
            ps.setInt(3, id)
            ps.setInt(4, Session.userId)
            ps.executeUpdate() > 0
        }
    }

    /** Soft delete. */
    suspend fun deactivate(id: Int): Boolean = setActive(id, false)

    suspend fun activate(id: Int): Boolean = setActive(id, true)

    private suspend fun setActive(id: Int, active: Boolean): Boolean = Db.withConnection { conn ->
        conn.prepareStatement(
            "UPDATE Categories SET IsActive = ? WHERE Id = ? AND UserId = ?"
        ).use { ps ->
            ps.setBoolean(1, active)
            ps.setInt(2, id)
            ps.setInt(3, Session.userId)
            ps.executeUpdate() > 0
        }
    }

    private fun bind(ps: java.sql.PreparedStatement, args: List<Any>): Int {
        args.forEachIndexed { i, a ->
            when (a) {
                is Int -> ps.setInt(i + 1, a)
                is String -> ps.setString(i + 1, a)
                else -> ps.setObject(i + 1, a)
            }
        }
        return args.size + 1
    }
}
