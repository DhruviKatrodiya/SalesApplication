package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.models.OrderStatus
import com.salesapp.mobile.data.models.PaymentStatus
import java.math.BigDecimal
import java.sql.Connection
import kotlin.math.max

/**
 * Kotlin port of the backend OrderMath. Recomputes an order's PaidAmount / RemainingAmount /
 * PaymentStatus from its Payments ledger and persists them (mirrors OrderMath.Recalculate).
 */
object OrderMath {

    const val METHOD_ADVANCE = "Advance"
    const val METHOD_ADVANCE_TRANSFER = "AdvanceTransfer"

    /** Recompute and UPDATE an order row from its payments. */
    fun recalculate(conn: Connection, orderId: Int) {
        val (total, statusInt) = conn.prepareStatement(
            "SELECT TotalAmount, Status FROM Orders WHERE Id = ?"
        ).use { ps ->
            ps.setInt(1, orderId)
            ps.executeQuery().use {
                if (!it.next()) return
                (it.getBigDecimal(1) ?: BigDecimal.ZERO) to it.getInt(2)
            }
        }
        val paid = conn.prepareStatement("SELECT COALESCE(SUM(Amount), 0) FROM Payments WHERE OrderId = ?").use { ps ->
            ps.setInt(1, orderId); ps.executeQuery().use { if (it.next()) it.getBigDecimal(1) ?: BigDecimal.ZERO else BigDecimal.ZERO }
        }
        val remaining = total.subtract(paid).let { if (it.signum() < 0) BigDecimal.ZERO else it }
        val status = OrderStatus.from(statusInt)
        val paymentStatus = when {
            paid.signum() <= 0 -> PaymentStatus.Pending
            paid >= total -> PaymentStatus.Paid
            status == OrderStatus.Pending -> PaymentStatus.Advance
            else -> PaymentStatus.Partial
        }
        conn.prepareStatement(
            "UPDATE Orders SET PaidAmount = ?, RemainingAmount = ?, PaymentStatus = ? WHERE Id = ?"
        ).use { ps ->
            ps.setBigDecimal(1, paid); ps.setBigDecimal(2, remaining)
            ps.setInt(3, paymentStatus.value); ps.setInt(4, orderId); ps.executeUpdate()
        }
    }

    /** OverpaidOn: cancelled → all paid becomes advance; else paid over the total. */
    fun overpaidOn(status: OrderStatus, total: Double, paid: Double): Double =
        if (status == OrderStatus.Cancelled) max(0.0, paid) else max(0.0, paid - total)
}
