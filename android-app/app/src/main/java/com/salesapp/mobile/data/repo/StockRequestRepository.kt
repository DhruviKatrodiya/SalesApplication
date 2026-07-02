package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.models.Paged
import com.salesapp.mobile.data.models.PaymentStatus
import com.salesapp.mobile.data.models.StockRequest
import com.salesapp.mobile.data.models.StockRequestLine
import com.salesapp.mobile.data.models.StockRequestStatus
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement

/**
 * "My Orders" — a salesperson's inventory/stock requests, mirroring StockRequestsController.
 * Fulfilling a request receives its lines as priced FIFO batches (via [InventoryOps.receive]).
 */
class StockRequestRepository {

    suspend fun list(
        requestNumber: String? = null,
        date: String? = null,
        item: String? = null,
        status: StockRequestStatus? = null,
        paymentStatus: PaymentStatus? = null,
        active: String = "active",
        page: Int = 1,
        pageSize: Int = 500,
    ): Paged<StockRequest> = Db.withConnection { conn ->
        val uid = Session.userId
        val where = StringBuilder("WHERE r.SalesmanId = ?")
        val args = mutableListOf<Any>(uid)
        if (active != "all") { where.append(" AND r.IsActive = ?"); args.add(if (active == "inactive") 0 else 1) }
        if (!requestNumber.isNullOrBlank()) { where.append(" AND r.RequestNumber LIKE ?"); args.add("%${requestNumber.trim()}%") }
        if (!date.isNullOrBlank()) { where.append(" AND CAST(r.CreatedAt AS date) = ?"); args.add(date) }
        if (status != null) { where.append(" AND r.Status = ?"); args.add(status.value) }
        if (paymentStatus != null) { where.append(" AND r.PaymentStatus = ?"); args.add(paymentStatus.value) }
        if (!item.isNullOrBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM StockRequestItems ri INNER JOIN Items i ON i.Id = ri.ItemId WHERE ri.StockRequestId = r.Id AND i.Name LIKE ?)")
            args.add("%${item.trim()}%")
        }

        val total = conn.prepareStatement("SELECT COUNT(*) FROM StockRequests r $where").use { ps ->
            bind(ps, args); ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }
        val safePage = if (page < 1) 1 else page
        val sql = """
            SELECT r.Id, r.RequestNumber, r.CreatedAt, r.Status, r.TotalAmount, r.PaidAmount, r.RemainingAmount,
                   r.PaymentStatus, r.Notes, r.IsActive
            FROM StockRequests r
            $where
            ORDER BY r.CreatedAt DESC, r.Id DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """.trimIndent()
        val heads = conn.prepareStatement(sql).use { ps ->
            val n = bind(ps, args); ps.setInt(n, (safePage - 1) * pageSize); ps.setInt(n + 1, pageSize)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(mapHead(rs)) } }
        }
        val withItems = heads.map { it.copy(items = loadItems(conn, it.id)) }
        Paged(withItems, total, safePage, pageSize)
    }

    suspend fun create(notes: String?, lines: List<OrderLine>): Result<Int> = Db.withConnection { conn ->
        if (lines.isEmpty()) return@withConnection Result.failure(IllegalArgumentException("At least one item is required."))
        if (lines.any { it.quantity <= 0 }) return@withConnection Result.failure(IllegalArgumentException("Quantities must be greater than zero."))
        conn.autoCommit = false
        try {
            val number = generateNumber(conn)
            val total = lines.sumOf { (it.unitPrice ?: itemPrice(conn, it.itemId)) * it.quantity }
            val id = conn.prepareStatement(
                "INSERT INTO StockRequests (RequestNumber, SalesmanId, Status, Notes, CreatedAt, TotalAmount, PaidAmount, RemainingAmount, PaymentStatus, IsActive) " +
                    "OUTPUT INSERTED.Id VALUES (?, ?, 0, ?, SYSUTCDATETIME(), ?, 0, ?, 0, 1)"
            ).use { ps ->
                ps.setString(1, number); ps.setInt(2, Session.userId); ps.setString(3, notes)
                ps.setBigDecimal(4, BigDecimal.valueOf(total)); ps.setBigDecimal(5, BigDecimal.valueOf(total))
                ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
            }
            insertLines(conn, id, lines)
            conn.commit(); Result.success(id)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    suspend fun update(id: Int, notes: String?, lines: List<OrderLine>): Result<Unit> = Db.withConnection { conn ->
        val h = head2(conn, id) ?: return@withConnection Result.failure(IllegalArgumentException("Request not found."))
        if (h.salesmanId != Session.userId) return@withConnection Result.failure(IllegalStateException("Not your request."))
        if (h.status != StockRequestStatus.Pending) return@withConnection Result.failure(IllegalStateException("Only pending requests can be edited."))
        if (lines.isEmpty()) return@withConnection Result.failure(IllegalArgumentException("At least one item is required."))
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM StockRequestItems WHERE StockRequestId = ?").use { ps -> ps.setInt(1, id); ps.executeUpdate() }
            val total = lines.sumOf { (it.unitPrice ?: itemPrice(conn, it.itemId)) * it.quantity }
            conn.prepareStatement("UPDATE StockRequests SET Notes = ?, TotalAmount = ? WHERE Id = ?").use { ps ->
                ps.setString(1, notes); ps.setBigDecimal(2, BigDecimal.valueOf(total)); ps.setInt(3, id); ps.executeUpdate()
            }
            insertLines(conn, id, lines)
            StockReqMath.recalculate(conn, id)
            conn.commit(); Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    /** Fulfil: receive each line as a priced batch, bump item price, mark Fulfilled. */
    suspend fun fulfill(id: Int): Result<Unit> = Db.withConnection { conn ->
        val h = head2(conn, id) ?: return@withConnection Result.failure(IllegalArgumentException("Request not found."))
        if (h.salesmanId != Session.userId) return@withConnection Result.failure(IllegalStateException("Not your request."))
        if (h.status != StockRequestStatus.Pending) return@withConnection Result.failure(IllegalStateException("Only pending requests can be fulfilled."))
        conn.autoCommit = false
        try {
            for (line in loadItems(conn, id)) {
                val price = BigDecimal.valueOf(line.unitPrice)
                InventoryOps.receive(conn, line.itemId, line.quantity, price, "Fulfill", id, h.requestNumber)
                conn.prepareStatement("UPDATE Items SET UnitPrice = ? WHERE Id = ?").use { ps -> ps.setBigDecimal(1, price); ps.setInt(2, line.itemId); ps.executeUpdate() }
            }
            setStatus(conn, id, StockRequestStatus.Fulfilled)
            StockReqMath.recalculate(conn, id)
            conn.commit(); Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    suspend fun done(id: Int): Result<Unit> = Db.withConnection { conn ->
        val h = head2(conn, id) ?: return@withConnection Result.failure(IllegalArgumentException("Request not found."))
        if (h.salesmanId != Session.userId) return@withConnection Result.failure(IllegalStateException("Not your request."))
        if (h.status != StockRequestStatus.Fulfilled) return@withConnection Result.failure(IllegalStateException("Only fulfilled requests can be marked done."))
        setStatus(conn, id, StockRequestStatus.Done); StockReqMath.recalculate(conn, id)
        Result.success(Unit)
    }

    /** Cancel: if fulfilled, take back the still-remaining received quantity and deplete its batches. */
    suspend fun cancel(id: Int): Result<Unit> = Db.withConnection { conn ->
        val h = head2(conn, id) ?: return@withConnection Result.failure(IllegalArgumentException("Request not found."))
        if (h.salesmanId != Session.userId) return@withConnection Result.failure(IllegalStateException("Not your request."))
        if (h.status == StockRequestStatus.Cancelled || h.status == StockRequestStatus.Done)
            return@withConnection Result.failure(IllegalStateException("This request can no longer be cancelled."))
        conn.autoCommit = false
        try {
            if (h.status == StockRequestStatus.Fulfilled) {
                data class B(val id: Int, val itemId: Int, val qty: Int)
                val batches = conn.prepareStatement("SELECT Id, ItemId, Quantity FROM InventoryBatches WHERE StockRequestId = ?").use { ps ->
                    ps.setInt(1, id); ps.executeQuery().use { rs -> buildList { while (rs.next()) add(B(rs.getInt(1), rs.getInt(2), rs.getInt(3))) } }
                }
                for (b in batches) {
                    InventoryOps.adjustStock(conn, b.itemId, -b.qty)   // remaining only
                    conn.prepareStatement("UPDATE InventoryBatches SET Quantity = 0 WHERE Id = ?").use { ps -> ps.setInt(1, b.id); ps.executeUpdate() }
                }
            }
            setStatus(conn, id, StockRequestStatus.Cancelled); StockReqMath.recalculate(conn, id)
            conn.commit(); Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    suspend fun deactivate(id: Int) = setActive(id, false)
    suspend fun activate(id: Int) = setActive(id, true)

    private suspend fun setActive(id: Int, active: Boolean): Boolean = Db.withConnection { conn ->
        conn.prepareStatement("UPDATE StockRequests SET IsActive = ? WHERE Id = ? AND SalesmanId = ?").use { ps ->
            ps.setBoolean(1, active); ps.setInt(2, id); ps.setInt(3, Session.userId); ps.executeUpdate() > 0
        }
    }

    // ---- internals ----

    private data class Head2(val salesmanId: Int, val status: StockRequestStatus, val requestNumber: String)
    private fun head2(conn: Connection, id: Int): Head2? =
        conn.prepareStatement("SELECT SalesmanId, Status, RequestNumber FROM StockRequests WHERE Id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { if (it.next()) Head2(it.getInt(1), StockRequestStatus.from(it.getInt(2)), it.getString(3) ?: "") else null }
        }

    private fun setStatus(conn: Connection, id: Int, s: StockRequestStatus) =
        conn.prepareStatement("UPDATE StockRequests SET Status = ? WHERE Id = ?").use { ps -> ps.setInt(1, s.value); ps.setInt(2, id); ps.executeUpdate() }

    private fun itemPrice(conn: Connection, itemId: Int): Double =
        conn.prepareStatement("SELECT UnitPrice FROM Items WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setInt(1, itemId); ps.setInt(2, Session.userId); ps.executeQuery().use { if (it.next()) it.getBigDecimal(1)?.toDouble() ?: 0.0 else 0.0 }
        }

    private fun insertLines(conn: Connection, requestId: Int, lines: List<OrderLine>) {
        for (line in lines) {
            val price = line.unitPrice ?: itemPrice(conn, line.itemId)
            conn.prepareStatement(
                "INSERT INTO StockRequestItems (StockRequestId, ItemId, Quantity, UnitPrice, LineTotal) VALUES (?, ?, ?, ?, ?)"
            ).use { ps ->
                ps.setInt(1, requestId); ps.setInt(2, line.itemId); ps.setInt(3, line.quantity)
                ps.setBigDecimal(4, BigDecimal.valueOf(price)); ps.setBigDecimal(5, BigDecimal.valueOf(price * line.quantity))
                ps.executeUpdate()
            }
        }
    }

    private fun generateNumber(conn: Connection): String {
        var seq = conn.prepareStatement(
            "SELECT COUNT(*) FROM StockRequests WHERE YEAR(CreatedAt) = YEAR(SYSUTCDATETIME()) AND MONTH(CreatedAt) = MONTH(SYSUTCDATETIME())"
        ).use { ps -> ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 } } + 1
        val datePart = conn.prepareStatement("SELECT FORMAT(SYSUTCDATETIME(), 'yy-MM-dd')").use { ps ->
            ps.executeQuery().use { if (it.next()) it.getString(1) else "" }
        }
        fun fmt(s: Int) = "REQ-$datePart-${s.toString().padStart(6, '0')}"
        var number = fmt(seq)
        while (conn.prepareStatement("SELECT 1 FROM StockRequests WHERE RequestNumber = ?").use { ps -> ps.setString(1, number); ps.executeQuery().use { it.next() } }) {
            seq++; number = fmt(seq)
        }
        return number
    }

    private fun loadItems(conn: Connection, requestId: Int): List<StockRequestLine> =
        conn.prepareStatement(
            """
            SELECT ri.ItemId, i.Name, ri.Quantity, i.StockQuantity, ri.UnitPrice, ri.LineTotal
            FROM StockRequestItems ri INNER JOIN Items i ON i.Id = ri.ItemId
            WHERE ri.StockRequestId = ? ORDER BY ri.Id
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, requestId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        StockRequestLine(
                            itemId = rs.getInt("ItemId"), itemName = rs.getString("Name") ?: "",
                            quantity = rs.getInt("Quantity"), currentStock = rs.getInt("StockQuantity"),
                            unitPrice = rs.getBigDecimal("UnitPrice")?.toDouble() ?: 0.0,
                            lineTotal = rs.getBigDecimal("LineTotal")?.toDouble() ?: 0.0,
                        )
                    )
                }
            }
        }

    private fun mapHead(rs: java.sql.ResultSet) = StockRequest(
        id = rs.getInt("Id"),
        requestNumber = rs.getString("RequestNumber") ?: "",
        createdAt = rs.getString("CreatedAt"),
        status = StockRequestStatus.from(rs.getInt("Status")),
        totalAmount = rs.getBigDecimal("TotalAmount")?.toDouble() ?: 0.0,
        paidAmount = rs.getBigDecimal("PaidAmount")?.toDouble() ?: 0.0,
        remainingAmount = rs.getBigDecimal("RemainingAmount")?.toDouble() ?: 0.0,
        paymentStatus = PaymentStatus.from(rs.getInt("PaymentStatus")),
        notes = rs.getString("Notes"),
        isActive = rs.getBoolean("IsActive"),
    )

    private fun bind(ps: PreparedStatement, args: List<Any>): Int {
        args.forEachIndexed { i, a -> when (a) {
            is Int -> ps.setInt(i + 1, a); is String -> ps.setString(i + 1, a); else -> ps.setObject(i + 1, a)
        } }
        return args.size + 1
    }
}
