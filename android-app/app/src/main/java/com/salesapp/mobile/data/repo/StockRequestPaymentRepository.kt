package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.models.StockRequestPayment
import java.math.BigDecimal
import java.sql.Connection
import kotlin.math.max
import kotlin.math.min

/** Payments against a salesperson's own stock requests, mirroring StockRequestPaymentsController. */
class StockRequestPaymentRepository {

    suspend fun byRequest(requestId: Int): List<StockRequestPayment> = Db.withConnection { conn ->
        conn.prepareStatement(
            "SELECT Id, StockRequestId, Amount, PaymentDate, Method, Note FROM StockRequestPayments WHERE StockRequestId = ? ORDER BY PaymentDate DESC"
        ).use { ps ->
            ps.setInt(1, requestId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        StockRequestPayment(
                            id = rs.getInt("Id"), stockRequestId = rs.getInt("StockRequestId"),
                            amount = rs.getBigDecimal("Amount")?.toDouble() ?: 0.0,
                            paymentDate = rs.getString("PaymentDate"),
                            method = rs.getString("Method"), note = rs.getString("Note"),
                        )
                    )
                }
            }
        }
    }

    suspend fun add(requestId: Int, amount: Double, method: String?, note: String?): Result<Unit> = Db.withConnection { conn ->
        if (!owns(conn, requestId)) return@withConnection Result.failure(IllegalStateException("Not your request."))
        if (amount <= 0) return@withConnection Result.failure(IllegalArgumentException("Amount must be greater than zero."))
        conn.autoCommit = false
        try {
            insertPayment(conn, requestId, amount, method, note)
            StockReqMath.recalculate(conn, requestId)
            conn.commit(); Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    suspend fun settle(requestId: Int): Result<Unit> = Db.withConnection { conn ->
        if (!owns(conn, requestId)) return@withConnection Result.failure(IllegalStateException("Not your request."))
        conn.autoCommit = false
        try {
            val total = totalOf(conn, requestId)
            val remaining = total - paidOf(conn, requestId)
            if (remaining > 0) insertPayment(conn, requestId, remaining, "Settlement", "Full settlement")
            StockReqMath.recalculate(conn, requestId)
            conn.commit(); Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    suspend fun deletePayment(id: Int): Result<Unit> = Db.withConnection { conn ->
        val (requestId, method) = conn.prepareStatement("SELECT StockRequestId, Method FROM StockRequestPayments WHERE Id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { if (it.next()) it.getInt(1) to it.getString(2) else return@withConnection Result.failure(IllegalArgumentException("Payment not found.")) }
        }
        if (!owns(conn, requestId)) return@withConnection Result.failure(IllegalStateException("Not your request."))
        if (method == OrderMath.METHOD_ADVANCE || method == OrderMath.METHOD_ADVANCE_TRANSFER)
            return@withConnection Result.failure(IllegalStateException("Advance entries can't be deleted directly."))
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM StockRequestPayments WHERE Id = ?").use { ps -> ps.setInt(1, id); ps.executeUpdate() }
            StockReqMath.recalculate(conn, requestId)
            conn.commit(); Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    /** Advance balance = overpayment across the salesperson's active requests. */
    suspend fun advanceBalance(): Double = Db.withConnection { conn ->
        conn.prepareStatement(
            """
            SELECT COALESCE(SUM(CASE WHEN paid > TotalAmount THEN paid - TotalAmount ELSE 0 END), 0)
            FROM (
              SELECT r.TotalAmount,
                     (SELECT COALESCE(SUM(p.Amount),0) FROM StockRequestPayments p WHERE p.StockRequestId = r.Id) AS paid
              FROM StockRequests r WHERE r.SalesmanId = ? AND r.IsActive = 1
            ) t
            """.trimIndent()
        ).use { ps -> ps.setInt(1, Session.userId); ps.executeQuery().use { if (it.next()) it.getBigDecimal(1)?.toDouble() ?: 0.0 else 0.0 } }
    }

    suspend fun applyAdvance(requestId: Int, amount: Double? = null): Result<Unit> = Db.withConnection { conn ->
        if (!owns(conn, requestId)) return@withConnection Result.failure(IllegalStateException("Not your request."))
        val number = conn.prepareStatement("SELECT RequestNumber FROM StockRequests WHERE Id = ?").use { ps ->
            ps.setInt(1, requestId); ps.executeQuery().use { if (it.next()) it.getString(1) ?: "" else "" }
        }

        data class Row(val id: Int, val createdAt: String?, val total: Double, val paid: Double)
        val rows = conn.prepareStatement(
            """
            SELECT r.Id, r.CreatedAt, r.TotalAmount,
                   (SELECT COALESCE(SUM(p.Amount),0) FROM StockRequestPayments p WHERE p.StockRequestId = r.Id) AS Paid
            FROM StockRequests r WHERE r.SalesmanId = ? AND r.IsActive = 1
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, Session.userId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(Row(rs.getInt(1), rs.getString(2), rs.getBigDecimal(3)?.toDouble() ?: 0.0, rs.getBigDecimal(4)?.toDouble() ?: 0.0)) } }
        }
        fun overpaid(r: Row) = StockReqMath.overpaidOn(r.total, r.paid)
        val available = rows.sumOf { overpaid(it) }
        val target = rows.first { it.id == requestId }
        val targetRemaining = max(0.0, target.total - target.paid)
        var toApply = min(available, targetRemaining)
        if (amount != null && amount > 0) toApply = min(toApply, amount)
        if (toApply <= 0) return@withConnection Result.failure(IllegalStateException("No advance balance available to apply."))

        conn.autoCommit = false
        try {
            var left = toApply
            for (src in rows.filter { it.id != requestId && overpaid(it) > 0 }.sortedBy { it.createdAt }) {
                if (left <= 0) break
                val take = min(left, overpaid(src))
                insertPayment(conn, src.id, -take, OrderMath.METHOD_ADVANCE_TRANSFER, "Advance applied to $number")
                StockReqMath.recalculate(conn, src.id)
                left -= take
            }
            insertPayment(conn, requestId, toApply, OrderMath.METHOD_ADVANCE, "Applied from advance balance")
            StockReqMath.recalculate(conn, requestId)
            conn.commit(); Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    // ---- internals ----

    private fun owns(conn: Connection, requestId: Int): Boolean =
        conn.prepareStatement("SELECT 1 FROM StockRequests WHERE Id = ? AND SalesmanId = ?").use { ps ->
            ps.setInt(1, requestId); ps.setInt(2, Session.userId); ps.executeQuery().use { it.next() }
        }

    private fun totalOf(conn: Connection, requestId: Int): Double =
        conn.prepareStatement("SELECT TotalAmount FROM StockRequests WHERE Id = ?").use { ps ->
            ps.setInt(1, requestId); ps.executeQuery().use { if (it.next()) it.getBigDecimal(1)?.toDouble() ?: 0.0 else 0.0 }
        }

    private fun paidOf(conn: Connection, requestId: Int): Double =
        conn.prepareStatement("SELECT COALESCE(SUM(Amount),0) FROM StockRequestPayments WHERE StockRequestId = ?").use { ps ->
            ps.setInt(1, requestId); ps.executeQuery().use { if (it.next()) it.getBigDecimal(1)?.toDouble() ?: 0.0 else 0.0 }
        }

    private fun insertPayment(conn: Connection, requestId: Int, amount: Double, method: String?, note: String?) {
        conn.prepareStatement(
            "INSERT INTO StockRequestPayments (StockRequestId, Amount, PaymentDate, Method, Note) VALUES (?, ?, SYSUTCDATETIME(), ?, ?)"
        ).use { ps ->
            ps.setInt(1, requestId); ps.setBigDecimal(2, BigDecimal.valueOf(amount)); ps.setString(3, method); ps.setString(4, note)
            ps.executeUpdate()
        }
    }
}
