package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.models.OrderStatus
import com.salesapp.mobile.data.models.PaymentStatus
import java.sql.Connection
import kotlin.math.max

/**
 * Recomputes an order's PaidAmount / RemainingAmount / PaymentStatus from its Payments ledger
 * and persists them (mirrors the backend OrderMath.Recalculate).
 */
object OrderMath {

    const val METHOD_ADVANCE = "Advance"
    const val METHOD_ADVANCE_TRANSFER = "AdvanceTransfer"

    fun recalculate(conn: Connection, orderId: Int) {
        val (total, statusInt) = conn.prepareStatement("SELECT TotalAmount, Status FROM Orders WHERE Id = ?").use { ps ->
            ps.setInt(1, orderId)
            ps.executeQuery().use { if (!it.next()) return else it.getDouble(1) to it.getInt(2) }
        }
        val paid = conn.prepareStatement("SELECT COALESCE(SUM(Amount), 0) FROM Payments WHERE OrderId = ?").use { ps ->
            ps.setInt(1, orderId); ps.executeQuery().use { if (it.next()) it.getDouble(1) else 0.0 }
        }
        val remaining = max(0.0, Sql.round2(total - paid))
        val status = OrderStatus.from(statusInt)
        val paymentStatus = when {
            paid <= 0.0 -> PaymentStatus.Pending
            paid >= total -> PaymentStatus.Paid
            status == OrderStatus.Pending -> PaymentStatus.Advance
            else -> PaymentStatus.Partial
        }
        conn.prepareStatement("UPDATE Orders SET PaidAmount = ?, RemainingAmount = ?, PaymentStatus = ? WHERE Id = ?").use { ps ->
            ps.setDouble(1, Sql.round2(paid)); ps.setDouble(2, remaining); ps.setInt(3, paymentStatus.value); ps.setInt(4, orderId)
            ps.executeUpdate()
        }
    }

    /** Cancelled → all paid becomes advance; else paid over the total. */
    fun overpaidOn(status: OrderStatus, total: Double, paid: Double): Double =
        if (status == OrderStatus.Cancelled) max(0.0, paid) else max(0.0, paid - total)
}
