package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.models.PaymentStatus
import com.salesapp.mobile.data.models.StockRequestStatus
import java.math.BigDecimal
import java.sql.Connection
import kotlin.math.max

/**
 * Payment recompute for stock requests (RecalcPayment in StockRequestsController).
 * Differs from OrderMath: Paid requires Total > 0, and Advance/Partial keys off the request status.
 */
object StockReqMath {

    fun recalculate(conn: Connection, requestId: Int) {
        val (total, statusInt) = conn.prepareStatement(
            "SELECT TotalAmount, Status FROM StockRequests WHERE Id = ?"
        ).use { ps ->
            ps.setInt(1, requestId)
            ps.executeQuery().use { if (!it.next()) return else (it.getBigDecimal(1) ?: BigDecimal.ZERO) to it.getInt(2) }
        }
        val paid = conn.prepareStatement("SELECT COALESCE(SUM(Amount),0) FROM StockRequestPayments WHERE StockRequestId = ?").use { ps ->
            ps.setInt(1, requestId); ps.executeQuery().use { if (it.next()) it.getBigDecimal(1) ?: BigDecimal.ZERO else BigDecimal.ZERO }
        }
        val remaining = total.subtract(paid).let { if (it.signum() < 0) BigDecimal.ZERO else it }
        val status = StockRequestStatus.from(statusInt)
        val paymentStatus = when {
            paid.signum() <= 0 -> PaymentStatus.Pending
            paid >= total && total.signum() > 0 -> PaymentStatus.Paid
            status == StockRequestStatus.Pending -> PaymentStatus.Advance
            else -> PaymentStatus.Partial
        }
        conn.prepareStatement(
            "UPDATE StockRequests SET PaidAmount = ?, RemainingAmount = ?, PaymentStatus = ? WHERE Id = ?"
        ).use { ps ->
            ps.setBigDecimal(1, paid); ps.setBigDecimal(2, remaining)
            ps.setInt(3, paymentStatus.value); ps.setInt(4, requestId); ps.executeUpdate()
        }
    }

    fun overpaidOn(total: Double, paid: Double): Double = max(0.0, paid - total)
}
