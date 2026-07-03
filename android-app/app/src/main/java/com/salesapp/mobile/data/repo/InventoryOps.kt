package com.salesapp.mobile.data.repo

import java.sql.Connection

/**
 * On-device batch/lot inventory with FIFO consumption and a movement ledger (SQLite).
 * Every function operates on an already-open [Connection] so it takes part in the caller's
 * transaction. Business rules mirror the original backend InventoryService.
 */
object InventoryOps {

    private data class ItemRow(val userId: Int, val stock: Int, val unitPrice: Double, val createdAt: String?)

    private fun loadItem(conn: Connection, itemId: Int): ItemRow? =
        conn.prepareStatement("SELECT UserId, StockQuantity, UnitPrice, CreatedAt FROM Items WHERE Id = ?").use { ps ->
            ps.setInt(1, itemId)
            ps.executeQuery().use {
                if (!it.next()) null else ItemRow(it.getInt(1), it.getInt(2), it.getDouble(3), it.getString(4))
            }
        }

    private fun batchedQty(conn: Connection, itemId: Int): Int =
        conn.prepareStatement("SELECT COALESCE(SUM(Quantity), 0) FROM InventoryBatches WHERE ItemId = ?").use { ps ->
            ps.setInt(1, itemId); ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }

    /** Backfill an opening batch for stock not yet represented by batches. */
    fun ensureOpeningBatch(conn: Connection, itemId: Int) {
        val item = loadItem(conn, itemId) ?: return
        val batched = batchedQty(conn, itemId)
        if (batched >= item.stock) return
        val diff = item.stock - batched
        val created = item.createdAt ?: nowExpr(conn)
        conn.prepareStatement(
            "INSERT INTO InventoryBatches (UserId, ItemId, InitialQuantity, Quantity, PurchasePrice, CreatedAt) VALUES (?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setInt(1, item.userId); ps.setInt(2, itemId); ps.setInt(3, diff); ps.setInt(4, diff)
            ps.setDouble(5, item.unitPrice); ps.setString(6, created); ps.executeUpdate()
        }
        val batchId = Sql.lastInsertId(conn)
        insertTxn(conn, item.userId, itemId, batchId, isIn = true, qty = diff,
            unitCost = item.unitPrice, refType = "Opening", refId = null, source = "Opening stock", createdAt = created)
    }

    /** Receive stock as a new price-tagged batch. */
    fun receive(conn: Connection, itemId: Int, qty: Int, price: Double, refType: String, refId: Int?, source: String?) {
        if (qty <= 0) return
        ensureOpeningBatch(conn, itemId)
        val item = loadItem(conn, itemId) ?: return
        val stockRequestId = if (refType == "Fulfill") refId else null
        conn.prepareStatement(
            "INSERT INTO InventoryBatches (UserId, ItemId, InitialQuantity, Quantity, PurchasePrice, CreatedAt, StockRequestId) " +
                "VALUES (?, ?, ?, ?, ?, datetime('now'), ?)"
        ).use { ps ->
            ps.setInt(1, item.userId); ps.setInt(2, itemId); ps.setInt(3, qty); ps.setInt(4, qty); ps.setDouble(5, price)
            if (stockRequestId != null) ps.setInt(6, stockRequestId) else ps.setNull(6, java.sql.Types.INTEGER)
            ps.executeUpdate()
        }
        val batchId = Sql.lastInsertId(conn)
        adjustStock(conn, itemId, qty)
        insertTxn(conn, item.userId, itemId, batchId, isIn = true, qty = qty,
            unitCost = price, refType = refType, refId = refId, source = source, createdAt = null)
    }

    /** Consume qty FIFO (oldest batch first). Throws if insufficient. */
    fun consumeFifo(conn: Connection, itemId: Int, qty: Int, refType: String, refId: Int?, source: String?) {
        if (qty <= 0) return
        ensureOpeningBatch(conn, itemId)
        val item = loadItem(conn, itemId) ?: error("Item $itemId not found.")

        data class Batch(val id: Int, val qty: Int, val price: Double)
        val batches = conn.prepareStatement(
            "SELECT Id, Quantity, PurchasePrice FROM InventoryBatches WHERE ItemId = ? AND Quantity > 0 ORDER BY CreatedAt, Id"
        ).use { ps ->
            ps.setInt(1, itemId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(Batch(rs.getInt(1), rs.getInt(2), rs.getDouble(3))) } }
        }
        val available = batches.sumOf { it.qty }
        if (available < qty) error("Insufficient inventory stock. Available: $available, requested: $qty.")

        var remaining = qty
        for (b in batches) {
            if (remaining <= 0) break
            val take = minOf(remaining, b.qty)
            conn.prepareStatement("UPDATE InventoryBatches SET Quantity = Quantity - ? WHERE Id = ?").use { ps ->
                ps.setInt(1, take); ps.setInt(2, b.id); ps.executeUpdate()
            }
            insertTxn(conn, item.userId, itemId, b.id, isIn = false, qty = take,
                unitCost = b.price, refType = refType, refId = refId, source = source, createdAt = null)
            remaining -= take
        }
        adjustStock(conn, itemId, -qty)
    }

    /** Reverse all (non-reversed) OUT movements for a document — restores batch quantities and stock. */
    fun reverse(conn: Connection, refType: String, refId: Int) {
        data class Txn(val id: Int, val itemId: Int, val batchId: Int?, val qty: Int)
        val txns = conn.prepareStatement(
            "SELECT Id, ItemId, BatchId, Quantity FROM InventoryTransactions WHERE RefType = ? AND RefId = ? AND Type = 1 AND Reversed = 0"
        ).use { ps ->
            ps.setString(1, refType); ps.setInt(2, refId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val batchId = rs.getInt(3).let { if (rs.wasNull()) null else it }
                        add(Txn(rs.getInt(1), rs.getInt(2), batchId, rs.getInt(4)))
                    }
                }
            }
        }
        for (t in txns) {
            if (t.batchId != null) conn.prepareStatement("UPDATE InventoryBatches SET Quantity = Quantity + ? WHERE Id = ?").use { ps ->
                ps.setInt(1, t.qty); ps.setInt(2, t.batchId); ps.executeUpdate()
            }
            adjustStock(conn, t.itemId, t.qty)
            conn.prepareStatement("UPDATE InventoryTransactions SET Reversed = 1 WHERE Id = ?").use { ps ->
                ps.setInt(1, t.id); ps.executeUpdate()
            }
        }
    }

    fun hasLinkedOut(conn: Connection, refType: String, refId: Int): Boolean =
        conn.prepareStatement(
            "SELECT 1 FROM InventoryTransactions WHERE RefType = ? AND RefId = ? AND Type = 1 AND Reversed = 0"
        ).use { ps -> ps.setString(1, refType); ps.setInt(2, refId); ps.executeQuery().use { it.next() } }

    fun adjustStock(conn: Connection, itemId: Int, delta: Int) {
        conn.prepareStatement("UPDATE Items SET StockQuantity = StockQuantity + ? WHERE Id = ?").use { ps ->
            ps.setInt(1, delta); ps.setInt(2, itemId); ps.executeUpdate()
        }
    }

    fun adjustDispatchStock(conn: Connection, itemId: Int, delta: Int, clampZero: Boolean = false) {
        val sql = if (clampZero)
            "UPDATE Items SET DispatchStock = CASE WHEN DispatchStock + ? < 0 THEN 0 ELSE DispatchStock + ? END WHERE Id = ?"
        else "UPDATE Items SET DispatchStock = DispatchStock + ? WHERE Id = ?"
        conn.prepareStatement(sql).use { ps ->
            if (clampZero) { ps.setInt(1, delta); ps.setInt(2, delta); ps.setInt(3, itemId) }
            else { ps.setInt(1, delta); ps.setInt(2, itemId) }
            ps.executeUpdate()
        }
    }

    private fun nowExpr(conn: Connection): String =
        conn.prepareStatement("SELECT datetime('now')").use { ps -> ps.executeQuery().use { if (it.next()) it.getString(1) else "" } }

    private fun insertTxn(
        conn: Connection, userId: Int, itemId: Int, batchId: Int, isIn: Boolean, qty: Int,
        unitCost: Double, refType: String, refId: Int?, source: String?, createdAt: String?,
    ) {
        val createdExpr = if (createdAt != null) "?" else "datetime('now')"
        val sql = "INSERT INTO InventoryTransactions " +
            "(UserId, ItemId, BatchId, Type, Quantity, UnitCost, TotalCost, RefType, RefId, Source, Reversed, CreatedAt) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, $createdExpr)"
        conn.prepareStatement(sql).use { ps ->
            var i = 1
            ps.setInt(i++, userId); ps.setInt(i++, itemId); ps.setInt(i++, batchId)
            ps.setInt(i++, if (isIn) 0 else 1); ps.setInt(i++, qty)
            ps.setDouble(i++, unitCost); ps.setDouble(i++, Sql.round2(unitCost * qty))
            ps.setString(i++, refType)
            if (refId != null) ps.setInt(i++, refId) else ps.setNull(i++, java.sql.Types.INTEGER)
            ps.setString(i++, source)
            if (createdAt != null) ps.setString(i, createdAt)
            ps.executeUpdate()
        }
    }
}
