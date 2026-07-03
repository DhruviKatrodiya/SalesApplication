package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session

/** Headline counts for the dashboard, all scoped to the signed-in salesperson. */
data class DashboardStats(
    val activeItems: Int,
    val lowStock: Int,
    val customers: Int,
    val pendingOrders: Int,
    val outstanding: Double,
    val pendingRequests: Int,
)

class DashboardRepository {
    suspend fun stats(threshold: Int = 10): DashboardStats = Db.withConnection { conn ->
        val uid = Session.userId
        fun count(sql: String): Int = conn.prepareStatement(sql).use { ps ->
            ps.setInt(1, uid); ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }
        val activeItems = count("SELECT COUNT(*) FROM Items WHERE UserId = ? AND IsActive = 1")
        val lowStock = conn.prepareStatement("SELECT COUNT(*) FROM Items WHERE UserId = ? AND IsActive = 1 AND StockQuantity <= ?").use { ps ->
            ps.setInt(1, uid); ps.setInt(2, threshold); ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }
        val customers = count("SELECT COUNT(*) FROM Customers WHERE UserId = ? AND IsActive = 1")
        val pendingOrders = count("SELECT COUNT(*) FROM Orders WHERE SalesmanId = ? AND IsActive = 1 AND Status NOT IN (2,3,5)")
        val outstanding = conn.prepareStatement(
            "SELECT COALESCE(SUM(RemainingAmount),0) FROM Orders WHERE SalesmanId = ? AND IsActive = 1 AND Status <> 5"
        ).use { ps -> ps.setInt(1, uid); ps.executeQuery().use { if (it.next()) it.getDouble(1) else 0.0 } }
        val pendingRequests = count("SELECT COUNT(*) FROM StockRequests WHERE SalesmanId = ? AND IsActive = 1 AND Status = 0")
        DashboardStats(activeItems, lowStock, customers, pendingOrders, outstanding, pendingRequests)
    }
}
