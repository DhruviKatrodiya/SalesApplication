package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.models.PaymentStatus
import com.salesapp.mobile.data.models.StockRequestStatus
import java.sql.Connection
import kotlin.math.max

/**
 * Payment recompute for stock requests. Differs from OrderMath: Paid requires Total > 0,
 * and Advance/Partial keys off the request status.
 */
object StockReqMath {

    fun recalculate(conn: Connection, requestId: Int) {
        val (total, statusInt) = conn.prepareStatement("SELECT TotalAmount, Status FROM StockRequests WHERE Id = ?").use { ps ->
            ps.setInt(1, requestId)
            ps.executeQuery().use { if (!it.next()) return else it.getDouble(1) to it.getInt(2) }
        }
        val paid = conn.prepareStatement("SELECT COALESCE(SUM(Amount), 0) FROM StockRequestPayments WHERE StockRequestId = ?").use { ps ->
            ps.setInt(1, requestId); ps.executeQuery().use { if (it.next()) it.getDouble(1) else 0.0 }
        }
        val remaining = max(0.0, Sql.round2(total - paid))
        val status = StockRequestStatus.from(statusInt)
        val paymentStatus = when {
            paid <= 0.0 -> PaymentStatus.Pending
            paid >= total && total > 0.0 -> PaymentStatus.Paid
            status == StockRequestStatus.Pending -> PaymentStatus.Advance
            else -> PaymentStatus.Partial
        }
        conn.prepareStatement("UPDATE StockRequests SET PaidAmount = ?, RemainingAmount = ?, PaymentStatus = ? WHERE Id = ?").use { ps ->
            ps.setDouble(1, Sql.round2(paid)); ps.setDouble(2, remaining); ps.setInt(3, paymentStatus.value); ps.setInt(4, requestId)
            ps.executeUpdate()
        }
    }

    fun overpaidOn(total: Double, paid: Double): Double = max(0.0, paid - total)
}
