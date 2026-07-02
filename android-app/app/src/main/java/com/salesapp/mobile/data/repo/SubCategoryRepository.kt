package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.models.Paged
import com.salesapp.mobile.data.models.SubCategory
import java.sql.PreparedStatement

/**
 * CRUD for [SubCategory], mirroring SubCategoriesController: user-scoped, category join +
 * validation, item counts, soft-delete/activate, paginated + name search + category filter.
 */
class SubCategoryRepository {

    suspend fun list(
        categoryId: Int? = null,
        search: String? = null,
        active: String = "active",
        page: Int = 1,
        pageSize: Int = 500,
    ): Paged<SubCategory> = Db.withConnection { conn ->
        val uid = Session.userId
        val where = StringBuilder("WHERE s.UserId = ?")
        val args = mutableListOf<Any>(uid)
        if (active != "all") {
            where.append(" AND s.IsActive = ?")
            args.add(if (active == "inactive") 0 else 1)
        }
        if (categoryId != null) {
            where.append(" AND s.CategoryId = ?")
            args.add(categoryId)
        }
        if (!search.isNullOrBlank()) {
            where.append(" AND s.Name LIKE ?")
            args.add("%${search.trim()}%")
        }

        val total = conn.prepareStatement("SELECT COUNT(*) FROM SubCategories s $where").use { ps ->
            bind(ps, args); ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }

        val safePage = if (page < 1) 1 else page
        val sql = """
            SELECT s.Id, s.CategoryId, c.Name AS CategoryName, s.Name, s.Description, s.IsActive,
                   (SELECT COUNT(*) FROM Items i WHERE i.SubCategoryId = s.Id) AS ItemCount
            FROM SubCategories s
            INNER JOIN Categories c ON c.Id = s.CategoryId
            $where
            ORDER BY s.CreatedAt DESC, s.Id DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """.trimIndent()

        val items = conn.prepareStatement(sql).use { ps ->
            val n = bind(ps, args)
            ps.setInt(n, (safePage - 1) * pageSize)
            ps.setInt(n + 1, pageSize)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        SubCategory(
                            id = rs.getInt("Id"),
                            categoryId = rs.getInt("CategoryId"),
                            categoryName = rs.getString("CategoryName") ?: "",
                            name = rs.getString("Name") ?: "",
                            description = rs.getString("Description"),
                            itemCount = rs.getInt("ItemCount"),
                            isActive = rs.getBoolean("IsActive"),
                        )
                    )
                }
            }
        }
        Paged(items, total, safePage, pageSize)
    }

    private fun categoryOwned(conn: java.sql.Connection, categoryId: Int): Boolean =
        conn.prepareStatement("SELECT 1 FROM Categories WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setInt(1, categoryId); ps.setInt(2, Session.userId)
            ps.executeQuery().use { it.next() }
        }

    suspend fun create(categoryId: Int, name: String, description: String?): Result<Int> =
        Db.withConnection { conn ->
            if (!categoryOwned(conn, categoryId))
                return@withConnection Result.failure(IllegalArgumentException("Category not found."))
            val id = conn.prepareStatement(
                "INSERT INTO SubCategories (UserId, CategoryId, Name, Description, CreatedAt, IsActive) " +
                    "OUTPUT INSERTED.Id VALUES (?, ?, ?, ?, SYSUTCDATETIME(), 1)"
            ).use { ps ->
                ps.setInt(1, Session.userId); ps.setInt(2, categoryId)
                ps.setString(3, name.trim()); ps.setString(4, description?.trim())
                ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
            }
            Result.success(id)
        }

    suspend fun update(id: Int, categoryId: Int, name: String, description: String?): Result<Unit> =
        Db.withConnection { conn ->
            if (!categoryOwned(conn, categoryId))
                return@withConnection Result.failure(IllegalArgumentException("Category not found."))
            conn.prepareStatement(
                "UPDATE SubCategories SET CategoryId = ?, Name = ?, Description = ? WHERE Id = ? AND UserId = ?"
            ).use { ps ->
                ps.setInt(1, categoryId); ps.setString(2, name.trim())
                ps.setString(3, description?.trim()); ps.setInt(4, id); ps.setInt(5, Session.userId)
                ps.executeUpdate()
            }
            Result.success(Unit)
        }

    suspend fun deactivate(id: Int) = setActive(id, false)
    suspend fun activate(id: Int) = setActive(id, true)

    private suspend fun setActive(id: Int, active: Boolean): Boolean = Db.withConnection { conn ->
        conn.prepareStatement("UPDATE SubCategories SET IsActive = ? WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setBoolean(1, active); ps.setInt(2, id); ps.setInt(3, Session.userId)
            ps.executeUpdate() > 0
        }
    }

    private fun bind(ps: PreparedStatement, args: List<Any>): Int {
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
