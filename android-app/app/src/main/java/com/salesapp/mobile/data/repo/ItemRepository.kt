package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.models.InventoryBatch
import com.salesapp.mobile.data.models.InventoryMovement
import com.salesapp.mobile.data.models.Item
import com.salesapp.mobile.data.models.ItemPriceHistory
import com.salesapp.mobile.data.models.Paged
import java.sql.Connection
import java.sql.PreparedStatement

/**
 * CRUD + inventory reads for [Item] (local SQLite). Create with stock > 0 writes an opening
 * batch + IN movement; price-history does exact FIFO valuation; delete is blocked while referenced.
 */
class ItemRepository {

    suspend fun list(
        subCategoryId: Int? = null,
        lowStock: Boolean = false,
        threshold: Int = 10,
        category: String? = null,
        item: String? = null,
        sku: String? = null,
        active: String = "active",
        page: Int = 1,
        pageSize: Int = 500,
    ): Paged<Item> = Db.withConnection { conn ->
        val uid = Session.userId
        val where = StringBuilder("WHERE i.UserId = ?")
        val args = mutableListOf<Any>(uid)
        if (active != "all") { where.append(" AND i.IsActive = ?"); args.add(if (active == "inactive") 0 else 1) }
        if (subCategoryId != null) { where.append(" AND i.SubCategoryId = ?"); args.add(subCategoryId) }
        if (lowStock) { where.append(" AND i.StockQuantity <= ?"); args.add(threshold) }
        if (!category.isNullOrBlank()) { where.append(" AND (c.Name LIKE ? OR s.Name LIKE ?)"); args.add("%${category.trim()}%"); args.add("%${category.trim()}%") }
        if (!item.isNullOrBlank()) { where.append(" AND i.Name LIKE ?"); args.add("%${item.trim()}%") }
        if (!sku.isNullOrBlank()) { where.append(" AND i.Sku LIKE ?"); args.add("%${sku.trim()}%") }

        val joins = "INNER JOIN SubCategories s ON s.Id = i.SubCategoryId INNER JOIN Categories c ON c.Id = s.CategoryId"
        val total = conn.prepareStatement("SELECT COUNT(*) FROM Items i $joins $where").use { ps ->
            bind(ps, args); ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }
        val safePage = if (page < 1) 1 else page
        val sql = """
            SELECT i.Id, i.SubCategoryId, s.Name AS SubName, c.Name AS CatName,
                   i.Name, i.Sku, i.Unit, i.StockQuantity, i.DispatchStock, i.UnitPrice, i.IsActive
            FROM Items i
            $joins
            $where
            ORDER BY i.CreatedAt DESC, i.Id DESC
            LIMIT ?, ?
        """.trimIndent()
        val items = conn.prepareStatement(sql).use { ps ->
            val n = bind(ps, args); ps.setInt(n, (safePage - 1) * pageSize); ps.setInt(n + 1, pageSize)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(mapItem(rs)) } }
        }
        Paged(items, total, safePage, pageSize)
    }

    private fun subCategoryOwned(conn: Connection, subCategoryId: Int): Boolean =
        conn.prepareStatement("SELECT 1 FROM SubCategories WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setInt(1, subCategoryId); ps.setInt(2, Session.userId); ps.executeQuery().use { it.next() }
        }

    suspend fun create(
        subCategoryId: Int, name: String, sku: String?, unit: String?, stockQuantity: Int, unitPrice: Double,
    ): Result<Int> = Db.withConnection { conn ->
        if (!subCategoryOwned(conn, subCategoryId)) return@withConnection Result.failure(IllegalArgumentException("SubCategory not found."))
        conn.autoCommit = false
        try {
            conn.prepareStatement(
                "INSERT INTO Items (UserId, SubCategoryId, Name, Sku, Unit, StockQuantity, DispatchStock, UnitPrice, CreatedAt, IsActive) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 0, ?, datetime('now'), 1)"
            ).use { ps ->
                ps.setInt(1, Session.userId); ps.setInt(2, subCategoryId)
                ps.setString(3, name.trim()); ps.setString(4, sku?.trim()); ps.setString(5, unit?.trim())
                ps.setInt(6, stockQuantity); ps.setDouble(7, unitPrice); ps.executeUpdate()
            }
            val itemId = Sql.lastInsertId(conn)
            if (stockQuantity > 0) {
                conn.prepareStatement(
                    "INSERT INTO InventoryBatches (UserId, ItemId, InitialQuantity, Quantity, PurchasePrice, CreatedAt) VALUES (?, ?, ?, ?, ?, datetime('now'))"
                ).use { ps ->
                    ps.setInt(1, Session.userId); ps.setInt(2, itemId); ps.setInt(3, stockQuantity); ps.setInt(4, stockQuantity); ps.setDouble(5, unitPrice)
                    ps.executeUpdate()
                }
                val batchId = Sql.lastInsertId(conn)
                conn.prepareStatement(
                    "INSERT INTO InventoryTransactions (UserId, ItemId, BatchId, Type, Quantity, UnitCost, TotalCost, RefType, Source, Reversed, CreatedAt) " +
                        "VALUES (?, ?, ?, 0, ?, ?, ?, 'Opening', 'Opening stock', 0, datetime('now'))"
                ).use { ps ->
                    ps.setInt(1, Session.userId); ps.setInt(2, itemId); ps.setInt(3, batchId); ps.setInt(4, stockQuantity)
                    ps.setDouble(5, unitPrice); ps.setDouble(6, Sql.round2(unitPrice * stockQuantity)); ps.executeUpdate()
                }
            }
            conn.commit()
            Result.success(itemId)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    suspend fun update(
        id: Int, subCategoryId: Int, name: String, sku: String?, unit: String?, stockQuantity: Int, unitPrice: Double,
    ): Result<Unit> = Db.withConnection { conn ->
        if (!subCategoryOwned(conn, subCategoryId)) return@withConnection Result.failure(IllegalArgumentException("SubCategory not found."))
        conn.prepareStatement(
            "UPDATE Items SET SubCategoryId = ?, Name = ?, Sku = ?, Unit = ?, StockQuantity = ?, UnitPrice = ? WHERE Id = ? AND UserId = ?"
        ).use { ps ->
            ps.setInt(1, subCategoryId); ps.setString(2, name.trim()); ps.setString(3, sku?.trim()); ps.setString(4, unit?.trim())
            ps.setInt(5, stockQuantity); ps.setDouble(6, unitPrice); ps.setInt(7, id); ps.setInt(8, Session.userId); ps.executeUpdate()
        }
        Result.success(Unit)
    }

    suspend fun deactivate(id: Int): Result<Unit> = Db.withConnection { conn ->
        val places = mutableListOf<String>()
        if (exists(conn, "SELECT 1 FROM OrderItems WHERE ItemId = ?", id)) places.add("customer orders")
        if (exists(conn, "SELECT 1 FROM StockRequestItems WHERE ItemId = ?", id)) places.add("stock requests")
        if (exists(conn, "SELECT 1 FROM TruckStocks WHERE ItemId = ? AND Quantity > 0", id)) places.add("truck stock")
        if (exists(conn, "SELECT 1 FROM DispatchItems WHERE ItemId = ?", id)) places.add("dispatches")
        if (places.isNotEmpty())
            return@withConnection Result.failure(IllegalStateException(
                "This item is still used in ${places.joinToString(", ")} and cannot be deactivated. Remove it from there first."))
        conn.prepareStatement("UPDATE Items SET IsActive = 0 WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setInt(1, id); ps.setInt(2, Session.userId); ps.executeUpdate()
        }
        Result.success(Unit)
    }

    suspend fun activate(id: Int): Boolean = Db.withConnection { conn ->
        conn.prepareStatement("UPDATE Items SET IsActive = 1 WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setInt(1, id); ps.setInt(2, Session.userId); ps.executeUpdate() > 0
        }
    }

    suspend fun priceHistory(id: Int): ItemPriceHistory? = Db.withConnection { conn ->
        val (name, stock, price) = conn.prepareStatement(
            "SELECT Name, StockQuantity, UnitPrice FROM Items WHERE Id = ? AND UserId = ?"
        ).use { ps ->
            ps.setInt(1, id); ps.setInt(2, Session.userId)
            ps.executeQuery().use { if (!it.next()) return@withConnection null else Triple(it.getString("Name") ?: "", it.getInt("StockQuantity"), it.getDouble("UnitPrice")) }
        }
        val batches = conn.prepareStatement(
            """
            SELECT b.Id, b.InitialQuantity, b.Quantity, b.PurchasePrice, b.CreatedAt, r.RequestNumber
            FROM InventoryBatches b LEFT JOIN StockRequests r ON r.Id = b.StockRequestId
            WHERE b.ItemId = ? ORDER BY b.CreatedAt DESC, b.Id DESC
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        InventoryBatch(
                            id = rs.getInt("Id"), initialQuantity = rs.getInt("InitialQuantity"), remaining = rs.getInt("Quantity"),
                            purchasePrice = rs.getDouble("PurchasePrice"), createdAt = rs.getString("CreatedAt"),
                            sourceRequestNumber = rs.getString("RequestNumber"),
                        )
                    )
                }
            }
        }
        val remainingQty = batches.sumOf { it.remaining }
        val batchValue = batches.sumOf { it.purchasePrice * it.remaining }
        val unbatched = maxOf(0, stock - remainingQty)
        val stockValue = Sql.round2(batchValue + unbatched * price)
        val avgCost = if (stock > 0) Sql.round2(stockValue / stock) else price
        val oldest = if (batches.isNotEmpty()) batches.last().purchasePrice else price
        val latest = if (batches.isNotEmpty()) batches.first().purchasePrice else price
        ItemPriceHistory(id, name, stock, oldest, latest, avgCost, stockValue, batches)
    }

    suspend fun movements(id: Int, page: Int = 1, pageSize: Int = 100): Paged<InventoryMovement> = Db.withConnection { conn ->
        val total = conn.prepareStatement("SELECT COUNT(*) FROM InventoryTransactions WHERE ItemId = ?").use { ps ->
            ps.setInt(1, id); ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }
        val safePage = if (page < 1) 1 else page
        val list = conn.prepareStatement(
            """
            SELECT Id, Type, Quantity, UnitCost, TotalCost, RefType, Source, Reversed, CreatedAt
            FROM InventoryTransactions WHERE ItemId = ? ORDER BY CreatedAt DESC, Id DESC LIMIT ?, ?
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, id); ps.setInt(2, (safePage - 1) * pageSize); ps.setInt(3, pageSize)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        InventoryMovement(
                            id = rs.getInt("Id"), type = if (rs.getInt("Type") == 0) "IN" else "OUT",
                            quantity = rs.getInt("Quantity"), unitCost = rs.getDouble("UnitCost"), totalCost = rs.getDouble("TotalCost"),
                            refType = rs.getString("RefType") ?: "", source = rs.getString("Source"),
                            reversed = rs.getBoolean("Reversed"), createdAt = rs.getString("CreatedAt"),
                        )
                    )
                }
            }
        }
        Paged(list, total, safePage, pageSize)
    }

    private fun exists(conn: Connection, sql: String, id: Int): Boolean =
        conn.prepareStatement(sql).use { ps -> ps.setInt(1, id); ps.executeQuery().use { it.next() } }

    private fun mapItem(rs: java.sql.ResultSet) = Item(
        id = rs.getInt("Id"), subCategoryId = rs.getInt("SubCategoryId"),
        subCategoryName = rs.getString("SubName") ?: "", categoryName = rs.getString("CatName") ?: "",
        name = rs.getString("Name") ?: "", sku = rs.getString("Sku"), unit = rs.getString("Unit"),
        stockQuantity = rs.getInt("StockQuantity"), dispatchStock = rs.getInt("DispatchStock"),
        unitPrice = rs.getDouble("UnitPrice"), isActive = rs.getBoolean("IsActive"),
    )

    private fun bind(ps: PreparedStatement, args: List<Any>): Int {
        args.forEachIndexed { i, a -> when (a) {
            is Int -> ps.setInt(i + 1, a); is String -> ps.setString(i + 1, a); else -> ps.setObject(i + 1, a)
        } }
        return args.size + 1
    }
}
