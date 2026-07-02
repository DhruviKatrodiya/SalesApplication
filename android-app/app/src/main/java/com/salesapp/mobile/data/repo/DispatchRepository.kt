package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.models.Dispatch
import com.salesapp.mobile.data.models.DispatchLine
import com.salesapp.mobile.data.models.Paged
import java.sql.Connection
import java.sql.PreparedStatement

/**
 * Truck dispatches, mirroring DispatchController. Recording a dispatch FIFO-consumes godown stock,
 * loads the truck's own stock ledger, and merges into the same-day entry for a truck. Delete/activate
 * reverse/re-apply those stock effects. (In-line editing of a saved dispatch is deferred.)
 */
class DispatchRepository {

    suspend fun list(
        truck: String? = null,
        date: String? = null,             // "yyyy-MM-dd"
        active: String = "active",
        page: Int = 1,
        pageSize: Int = 500,
    ): Paged<Dispatch> = Db.withConnection { conn ->
        val uid = Session.userId
        val where = StringBuilder("WHERE d.UserId = ?")
        val args = mutableListOf<Any>(uid)
        if (active != "all") { where.append(" AND d.IsActive = ?"); args.add(if (active == "inactive") 0 else 1) }
        if (!truck.isNullOrBlank()) { where.append(" AND d.TruckLabel = ?"); args.add(truck.trim()) }
        if (!date.isNullOrBlank()) { where.append(" AND CAST(d.DispatchDate AS date) = ?"); args.add(date) }

        val total = conn.prepareStatement("SELECT COUNT(*) FROM Dispatches d $where").use { ps ->
            bind(ps, args); ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }
        val safePage = if (page < 1) 1 else page
        val sql = """
            SELECT d.Id, d.DispatchDate, d.TruckLabel, d.Notes, d.IsActive
            FROM Dispatches d
            $where
            ORDER BY d.DispatchDate DESC, d.Id DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """.trimIndent()
        val heads = conn.prepareStatement(sql).use { ps ->
            val n = bind(ps, args); ps.setInt(n, (safePage - 1) * pageSize); ps.setInt(n + 1, pageSize)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        Dispatch(
                            id = rs.getInt("Id"),
                            dispatchDate = rs.getString("DispatchDate"),
                            truckLabel = rs.getString("TruckLabel") ?: "",
                            notes = rs.getString("Notes"),
                            isActive = rs.getBoolean("IsActive"),
                        )
                    )
                }
            }
        }
        val withItems = heads.map { it.copy(items = loadItems(conn, it.id)) }
        Paged(withItems, total, safePage, pageSize)
    }

    suspend fun truckLabels(): List<String> = Db.withConnection { conn ->
        conn.prepareStatement("SELECT DISTINCT TruckLabel FROM Dispatches WHERE UserId = ? ORDER BY TruckLabel").use { ps ->
            ps.setInt(1, Session.userId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1) ?: "") } }
        }
    }

    /** Record a dispatch: validate stock, FIFO-consume, load the truck, merge same-day. */
    suspend fun create(truckLabel: String?, notes: String?, lines: List<Pair<Int, Int>>): Result<Int> =
        Db.withConnection { conn ->
            val uid = Session.userId
            if (lines.isEmpty()) return@withConnection Result.failure(IllegalArgumentException("At least one item is required."))
            conn.autoCommit = false
            try {
                for ((itemId, qty) in lines) {
                    val item = itemRow(conn, itemId, uid) ?: throw IllegalArgumentException("Item $itemId not found.")
                    if (qty <= 0) throw IllegalArgumentException("Quantity for '${item.first}' must be greater than zero.")
                    if (item.second < qty) throw IllegalStateException("Insufficient stock for '${item.first}'. Available: ${item.second}, requested: $qty.")
                }
                val truck = truckLabel?.trim().takeUnless { it.isNullOrEmpty() } ?: "Truck-1"
                val truckId = ensureTruck(conn, uid, truck)
                val dispatchId = sameDayDispatch(conn, uid, truck) ?: conn.prepareStatement(
                    "INSERT INTO Dispatches (UserId, TruckLabel, Notes, DispatchDate, IsActive) " +
                        "OUTPUT INSERTED.Id VALUES (?, ?, ?, SYSUTCDATETIME(), 1)"
                ).use { ps ->
                    ps.setInt(1, uid); ps.setString(2, truck); ps.setString(3, notes)
                    ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
                }
                if (!notes.isNullOrBlank()) conn.prepareStatement("UPDATE Dispatches SET Notes = ? WHERE Id = ?").use { ps ->
                    ps.setString(1, notes); ps.setInt(2, dispatchId); ps.executeUpdate()
                }

                for ((itemId, qty) in lines) {
                    InventoryOps.consumeFifo(conn, itemId, qty, "Dispatch", dispatchId, truck)
                    InventoryOps.adjustDispatchStock(conn, itemId, qty)
                    addTruckStock(conn, truckId, itemId, qty)
                    mergeDispatchLine(conn, dispatchId, itemId, qty)
                }
                conn.commit(); Result.success(dispatchId)
            } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
        }

    /** Soft-delete: reverse the dispatch's stock effects and flag inactive. */
    suspend fun delete(id: Int): Result<Unit> = Db.withConnection { conn ->
        val d = head(conn, id) ?: return@withConnection Result.failure(IllegalArgumentException("Dispatch not found."))
        if (d.userId != Session.userId) return@withConnection Result.failure(IllegalStateException("Not your dispatch."))
        if (!d.isActive) return@withConnection Result.success(Unit)
        conn.autoCommit = false
        try {
            reverseStock(conn, id, d.truckLabel)
            conn.prepareStatement("UPDATE Dispatches SET IsActive = 0 WHERE Id = ?").use { ps -> ps.setInt(1, id); ps.executeUpdate() }
            conn.commit(); Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    /** Reactivate: re-load the dispatch's stock onto the truck (FIFO), after checking availability. */
    suspend fun activate(id: Int): Result<Unit> = Db.withConnection { conn ->
        val d = head(conn, id) ?: return@withConnection Result.failure(IllegalArgumentException("Dispatch not found."))
        if (d.userId != Session.userId) return@withConnection Result.failure(IllegalStateException("Not your dispatch."))
        if (d.isActive) return@withConnection Result.success(Unit)
        conn.autoCommit = false
        try {
            val lines = loadItems(conn, id)
            for (line in lines) {
                val item = itemRow(conn, line.itemId, d.userId)
                    ?: throw IllegalStateException("Item '${line.itemName}' no longer exists; cannot reactivate.")
                if (item.second < line.quantity)
                    throw IllegalStateException("Insufficient stock for '${line.itemName}' to reactivate. Available: ${item.second}, needed: ${line.quantity}.")
            }
            val truckId = ensureTruck(conn, d.userId, d.truckLabel)
            for (line in lines) {
                InventoryOps.consumeFifo(conn, line.itemId, line.quantity, "Dispatch", id, d.truckLabel)
                InventoryOps.adjustDispatchStock(conn, line.itemId, line.quantity)
                addTruckStock(conn, truckId, line.itemId, line.quantity)
            }
            conn.prepareStatement("UPDATE Dispatches SET IsActive = 1 WHERE Id = ?").use { ps -> ps.setInt(1, id); ps.executeUpdate() }
            conn.commit(); Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    // ---- internals ----

    private fun reverseStock(conn: Connection, dispatchId: Int, truckLabel: String) {
        val linked = InventoryOps.hasLinkedOut(conn, "Dispatch", dispatchId)
        if (linked) InventoryOps.reverse(conn, "Dispatch", dispatchId)
        val truckId = existingTruck(conn, Session.userId, truckLabel)
        for (line in loadItems(conn, dispatchId)) {
            if (!linked) InventoryOps.adjustStock(conn, line.itemId, line.quantity)   // legacy: no movement link
            InventoryOps.adjustDispatchStock(conn, line.itemId, -line.quantity, clampZero = true)
            if (truckId != null) conn.prepareStatement(
                "UPDATE TruckStocks SET Quantity = CASE WHEN Quantity - ? < 0 THEN 0 ELSE Quantity - ? END WHERE TruckId = ? AND ItemId = ?"
            ).use { ps -> ps.setInt(1, line.quantity); ps.setInt(2, line.quantity); ps.setInt(3, truckId); ps.setInt(4, line.itemId); ps.executeUpdate() }
        }
    }

    private fun sameDayDispatch(conn: Connection, uid: Int, truck: String): Int? =
        conn.prepareStatement(
            "SELECT Id FROM Dispatches WHERE UserId = ? AND TruckLabel = ? AND CAST(DispatchDate AS date) = CAST(SYSUTCDATETIME() AS date)"
        ).use { ps -> ps.setInt(1, uid); ps.setString(2, truck); ps.executeQuery().use { if (it.next()) it.getInt(1) else null } }

    private fun mergeDispatchLine(conn: Connection, dispatchId: Int, itemId: Int, qty: Int) {
        val updated = conn.prepareStatement("UPDATE DispatchItems SET Quantity = Quantity + ? WHERE DispatchId = ? AND ItemId = ?").use { ps ->
            ps.setInt(1, qty); ps.setInt(2, dispatchId); ps.setInt(3, itemId); ps.executeUpdate()
        }
        if (updated == 0) conn.prepareStatement("INSERT INTO DispatchItems (DispatchId, ItemId, Quantity) VALUES (?, ?, ?)").use { ps ->
            ps.setInt(1, dispatchId); ps.setInt(2, itemId); ps.setInt(3, qty); ps.executeUpdate()
        }
    }

    private fun ensureTruck(conn: Connection, uid: Int, name: String): Int =
        existingTruck(conn, uid, name) ?: conn.prepareStatement(
            "INSERT INTO Trucks (UserId, Name, CreatedAt, IsActive) OUTPUT INSERTED.Id VALUES (?, ?, SYSUTCDATETIME(), 1)"
        ).use { ps -> ps.setInt(1, uid); ps.setString(2, name); ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 } }

    private fun existingTruck(conn: Connection, uid: Int, name: String): Int? =
        conn.prepareStatement("SELECT Id FROM Trucks WHERE UserId = ? AND Name = ?").use { ps ->
            ps.setInt(1, uid); ps.setString(2, name); ps.executeQuery().use { if (it.next()) it.getInt(1) else null }
        }

    private fun addTruckStock(conn: Connection, truckId: Int, itemId: Int, delta: Int) {
        val updated = conn.prepareStatement("UPDATE TruckStocks SET Quantity = Quantity + ? WHERE TruckId = ? AND ItemId = ?").use { ps ->
            ps.setInt(1, delta); ps.setInt(2, truckId); ps.setInt(3, itemId); ps.executeUpdate()
        }
        if (updated == 0) conn.prepareStatement("INSERT INTO TruckStocks (TruckId, ItemId, Quantity) VALUES (?, ?, ?)").use { ps ->
            ps.setInt(1, truckId); ps.setInt(2, itemId); ps.setInt(3, delta); ps.executeUpdate()
        }
    }

    /** Returns (name, stock) for an owned item. */
    private fun itemRow(conn: Connection, itemId: Int, uid: Int): Pair<String, Int>? =
        conn.prepareStatement("SELECT Name, StockQuantity FROM Items WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setInt(1, itemId); ps.setInt(2, uid)
            ps.executeQuery().use { if (it.next()) (it.getString(1) ?: "") to it.getInt(2) else null }
        }

    private data class Head(val userId: Int, val truckLabel: String, val isActive: Boolean)
    private fun head(conn: Connection, id: Int): Head? =
        conn.prepareStatement("SELECT UserId, TruckLabel, IsActive FROM Dispatches WHERE Id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { if (it.next()) Head(it.getInt(1), it.getString(2) ?: "", it.getBoolean(3)) else null }
        }

    private fun loadItems(conn: Connection, dispatchId: Int): List<DispatchLine> =
        conn.prepareStatement(
            "SELECT di.ItemId, i.Name, di.Quantity FROM DispatchItems di INNER JOIN Items i ON i.Id = di.ItemId WHERE di.DispatchId = ? ORDER BY i.Name"
        ).use { ps ->
            ps.setInt(1, dispatchId)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(DispatchLine(rs.getInt(1), rs.getString(2) ?: "", rs.getInt(3))) }
            }
        }

    private fun bind(ps: PreparedStatement, args: List<Any>): Int {
        args.forEachIndexed { i, a -> when (a) {
            is Int -> ps.setInt(i + 1, a); is String -> ps.setString(i + 1, a); else -> ps.setObject(i + 1, a)
        } }
        return args.size + 1
    }
}
