package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.models.OrderStatus
import com.salesapp.mobile.data.models.Payment
import java.sql.Connection
import kotlin.math.max
import kotlin.math.min

/** Payments ledger (local SQLite), mirroring PaymentsController. Recomputes the order via OrderMath. */
class PaymentRepository {

    suspend fun byOrder(orderId: Int): List<Payment> = Db.withConnection { conn ->
        conn.prepareStatement(
            "SELECT Id, OrderId, Amount, PaymentDate, Method, Note FROM Payments WHERE OrderId = ? ORDER BY PaymentDate DESC"
        ).use { ps ->
            ps.setInt(1, orderId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        Payment(
                            id = rs.getInt("Id"), orderId = rs.getInt("OrderId"), amount = rs.getDouble("Amount"),
                            paymentDate = rs.getString("PaymentDate"), method = rs.getString("Method"), note = rs.getString("Note"),
                        )
                    )
                }
            }
        }
    }

    suspend fun add(orderId: Int, amount: Double, method: String?, note: String?, paymentDate: String? = null): Result<Unit> = Db.withConnection { conn ->
        val h = header(conn, orderId) ?: return@withConnection Result.failure(IllegalArgumentException("Order not found."))
        if (h.salesmanId != Session.userId) return@withConnection Result.failure(IllegalStateException("You can only manage payments for your own orders."))
        if (h.status == OrderStatus.Cancelled) return@withConnection Result.failure(IllegalStateException("This order is cancelled."))
        if (amount <= 0) return@withConnection Result.failure(IllegalArgumentException("Amount must be greater than zero."))
        conn.autoCommit = false
        try {
            insertPayment(conn, orderId, amount, method, note, paymentDate)
            OrderMath.recalculate(conn, orderId)
            conn.commit(); Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    suspend fun settle(orderId: Int): Result<Unit> = Db.withConnection { conn ->
        val h = header(conn, orderId) ?: return@withConnection Result.failure(IllegalArgumentException("Order not found."))
        if (h.salesmanId != Session.userId) return@withConnection Result.failure(IllegalStateException("You can only manage payments for your own orders."))
        if (h.status == OrderStatus.Cancelled) return@withConnection Result.failure(IllegalStateException("This order is cancelled."))
        conn.autoCommit = false
        try {
            val remaining = Sql.round2(h.total - paidOf(conn, orderId))
            if (remaining > 0) insertPayment(conn, orderId, remaining, "Settlement", "Full settlement", null)
            OrderMath.recalculate(conn, orderId)
            conn.commit(); Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    suspend fun deletePayment(id: Int): Result<Unit> = Db.withConnection { conn ->
        val (orderId, method) = conn.prepareStatement("SELECT OrderId, Method FROM Payments WHERE Id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { if (it.next()) it.getInt(1) to it.getString(2) else return@withConnection Result.failure(IllegalArgumentException("Payment not found.")) }
        }
        if (!ownsOrder(conn, orderId)) return@withConnection Result.failure(IllegalStateException("You can only manage payments for your own orders."))
        if (method == OrderMath.METHOD_ADVANCE || method == OrderMath.METHOD_ADVANCE_TRANSFER)
            return@withConnection Result.failure(IllegalStateException("Advance entries can't be deleted directly."))
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM Payments WHERE Id = ?").use { ps -> ps.setInt(1, id); ps.executeUpdate() }
            OrderMath.recalculate(conn, orderId)
            conn.commit(); Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    suspend fun applyAdvance(orderId: Int, amount: Double? = null): Result<Unit> = Db.withConnection { conn ->
        val target = header(conn, orderId) ?: return@withConnection Result.failure(IllegalArgumentException("Order not found."))
        if (target.salesmanId != Session.userId) return@withConnection Result.failure(IllegalStateException("You can only manage payments for your own orders."))

        data class Row(val id: Int, val orderDate: String?, val status: OrderStatus, val total: Double, val paid: Double)
        val orders = conn.prepareStatement(
            """
            SELECT o.Id, o.OrderDate, o.Status, o.TotalAmount,
                   (SELECT COALESCE(SUM(p.Amount),0) FROM Payments p WHERE p.OrderId = o.Id) AS Paid
            FROM Orders o WHERE o.CustomerId = ? AND o.IsActive = 1
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, target.customerId)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(Row(rs.getInt(1), rs.getString(2), OrderStatus.from(rs.getInt(3)), rs.getDouble(4), rs.getDouble(5))) }
            }
        }
        fun overpaid(r: Row) = OrderMath.overpaidOn(r.status, r.total, r.paid)
        val available = orders.sumOf { overpaid(it) }
        val targetRow = orders.first { it.id == orderId }
        val targetRemaining = max(0.0, targetRow.total - targetRow.paid)
        var toApply = min(available, targetRemaining)
        if (amount != null && amount > 0) toApply = min(toApply, amount)
        if (toApply <= 0) return@withConnection Result.failure(IllegalStateException("No advance balance available to apply."))

        conn.autoCommit = false
        try {
            var left = toApply
            for (src in orders.filter { it.id != orderId && overpaid(it) > 0 }.sortedBy { it.orderDate }) {
                if (left <= 0) break
                val take = min(left, overpaid(src))
                insertPayment(conn, src.id, -take, OrderMath.METHOD_ADVANCE_TRANSFER, "Advance applied to ${target.orderNumber}", null)
                OrderMath.recalculate(conn, src.id)
                left -= take
            }
            insertPayment(conn, orderId, toApply, OrderMath.METHOD_ADVANCE, "Applied from advance balance", null)
            OrderMath.recalculate(conn, orderId)
            conn.commit(); Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    private data class Header(val salesmanId: Int, val customerId: Int, val status: OrderStatus, val total: Double, val orderNumber: String)
    private fun header(conn: Connection, orderId: Int): Header? =
        conn.prepareStatement("SELECT SalesmanId, CustomerId, Status, TotalAmount, OrderNumber FROM Orders WHERE Id = ?").use { ps ->
            ps.setInt(1, orderId)
            ps.executeQuery().use { if (!it.next()) null else Header(it.getInt(1), it.getInt(2), OrderStatus.from(it.getInt(3)), it.getDouble(4), it.getString(5) ?: "") }
        }

    private fun ownsOrder(conn: Connection, orderId: Int): Boolean =
        conn.prepareStatement("SELECT 1 FROM Orders WHERE Id = ? AND SalesmanId = ?").use { ps ->
            ps.setInt(1, orderId); ps.setInt(2, Session.userId); ps.executeQuery().use { it.next() }
        }

    private fun paidOf(conn: Connection, orderId: Int): Double =
        conn.prepareStatement("SELECT COALESCE(SUM(Amount),0) FROM Payments WHERE OrderId = ?").use { ps ->
            ps.setInt(1, orderId); ps.executeQuery().use { if (it.next()) it.getDouble(1) else 0.0 }
        }

    private fun insertPayment(conn: Connection, orderId: Int, amount: Double, method: String?, note: String?, paymentDate: String?) {
        val dateExpr = if (paymentDate.isNullOrBlank()) "datetime('now')" else "?"
        conn.prepareStatement("INSERT INTO Payments (OrderId, Amount, PaymentDate, Method, Note) VALUES (?, ?, $dateExpr, ?, ?)").use { ps ->
            var i = 1
            ps.setInt(i++, orderId); ps.setDouble(i++, Sql.round2(amount))
            if (!paymentDate.isNullOrBlank()) ps.setString(i++, paymentDate)
            ps.setString(i++, method); ps.setString(i, note)
            ps.executeUpdate()
        }
    }
}
