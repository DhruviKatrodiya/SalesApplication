package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.models.Paged
import com.salesapp.mobile.data.models.Truck
import com.salesapp.mobile.data.models.TruckStockItem
import java.sql.Connection

/** CRUD for trucks + per-truck stock (local SQLite): unique name per user, delete blocked while stock loaded. */
class TruckRepository {

    suspend fun list(
        search: String? = null,
        withStock: Boolean = false,
        active: String = "active",
        page: Int = 1,
        pageSize: Int = 500,
    ): Paged<Truck> = Db.withConnection { conn ->
        val uid = Session.userId
        val where = StringBuilder("WHERE t.UserId = ?")
        val args = mutableListOf<Any>(uid)
        if (active != "all") { where.append(" AND t.IsActive = ?"); args.add(if (active == "inactive") 0 else 1) }
        if (!search.isNullOrBlank()) { where.append(" AND t.Name LIKE ?"); args.add("%${search.trim()}%") }
        if (withStock) where.append(" AND EXISTS (SELECT 1 FROM TruckStocks s WHERE s.TruckId = t.Id AND s.Quantity > 0)")

        val sql = """
            SELECT t.Id, t.Name, t.CreatedAt, t.IsActive,
                   (SELECT COUNT(*) FROM TruckStocks s WHERE s.TruckId = t.Id AND s.Quantity > 0) AS ItemCount,
                   (SELECT COALESCE(SUM(s.Quantity), 0) FROM TruckStocks s WHERE s.TruckId = t.Id) AS TotalUnits
            FROM Trucks t $where ORDER BY t.CreatedAt DESC, t.Id DESC
        """.trimIndent()
        val all = conn.prepareStatement(sql).use { ps ->
            args.forEachIndexed { i, a -> ps.setObject(i + 1, a) }
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        Truck(
                            id = rs.getInt("Id"), name = rs.getString("Name") ?: "", createdAt = rs.getString("CreatedAt"),
                            itemCount = rs.getInt("ItemCount"), totalUnits = rs.getInt("TotalUnits"), isActive = rs.getBoolean("IsActive"),
                        )
                    )
                }
            }
        }
        val safePage = if (page < 1) 1 else page
        Paged(all.drop((safePage - 1) * pageSize).take(pageSize), all.size, safePage, pageSize)
    }

    suspend fun stock(truckId: Int): List<TruckStockItem> = Db.withConnection { conn ->
        conn.prepareStatement(
            """
            SELECT s.ItemId, i.Name, i.Sku, i.Unit, s.Quantity, i.UnitPrice
            FROM TruckStocks s INNER JOIN Items i ON i.Id = s.ItemId
            WHERE s.TruckId = ? AND s.Quantity > 0 ORDER BY i.Name
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, truckId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        TruckStockItem(
                            itemId = rs.getInt("ItemId"), name = rs.getString("Name") ?: "", sku = rs.getString("Sku"),
                            unit = rs.getString("Unit"), quantity = rs.getInt("Quantity"), unitPrice = rs.getDouble("UnitPrice"),
                        )
                    )
                }
            }
        }
    }

    private fun nameTaken(conn: Connection, name: String, excludeId: Int?): Boolean =
        conn.prepareStatement(
            "SELECT 1 FROM Trucks WHERE UserId = ? AND Name = ?" + (if (excludeId != null) " AND Id <> ?" else "")
        ).use { ps ->
            ps.setInt(1, Session.userId); ps.setString(2, name)
            if (excludeId != null) ps.setInt(3, excludeId)
            ps.executeQuery().use { it.next() }
        }

    suspend fun create(name: String): Result<Int> = Db.withConnection { conn ->
        val n = name.trim()
        if (n.isEmpty()) return@withConnection Result.failure(IllegalArgumentException("Truck name is required."))
        if (nameTaken(conn, n, null)) return@withConnection Result.failure(IllegalArgumentException("A truck with this name already exists."))
        conn.prepareStatement("INSERT INTO Trucks (UserId, Name, CreatedAt, IsActive) VALUES (?, ?, datetime('now'), 1)").use { ps ->
            ps.setInt(1, Session.userId); ps.setString(2, n); ps.executeUpdate()
        }
        Result.success(Sql.lastInsertId(conn))
    }

    suspend fun update(id: Int, name: String): Result<Unit> = Db.withConnection { conn ->
        val n = name.trim()
        if (n.isEmpty()) return@withConnection Result.failure(IllegalArgumentException("Truck name is required."))
        if (nameTaken(conn, n, id)) return@withConnection Result.failure(IllegalArgumentException("A truck with this name already exists."))
        conn.prepareStatement("UPDATE Trucks SET Name = ? WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setString(1, n); ps.setInt(2, id); ps.setInt(3, Session.userId); ps.executeUpdate()
        }
        Result.success(Unit)
    }

    suspend fun deactivate(id: Int): Result<Unit> = Db.withConnection { conn ->
        val hasStock = conn.prepareStatement("SELECT 1 FROM TruckStocks WHERE TruckId = ? AND Quantity > 0").use { ps ->
            ps.setInt(1, id); ps.executeQuery().use { it.next() }
        }
        if (hasStock) return@withConnection Result.failure(IllegalStateException("This truck still has stock loaded. Clear it before deleting."))
        conn.prepareStatement("UPDATE Trucks SET IsActive = 0 WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setInt(1, id); ps.setInt(2, Session.userId); ps.executeUpdate()
        }
        Result.success(Unit)
    }

    suspend fun activate(id: Int): Boolean = Db.withConnection { conn ->
        conn.prepareStatement("UPDATE Trucks SET IsActive = 1 WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setInt(1, id); ps.setInt(2, Session.userId); ps.executeUpdate() > 0
        }
    }
}
