package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.models.CustomerReportRow
import com.salesapp.mobile.data.models.ReportRow
import com.salesapp.mobile.data.models.ReportSummary
import java.sql.Connection

/**
 * Order-based reporting (local SQLite). Only active, non-cancelled orders count.
 * Delivered/Completed (status 2/3) are "delivered"; everything else is "pending".
 */
class ReportRepository {

    private val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private fun baseWhere() = "WHERE o.SalesmanId = ? AND o.IsActive = 1 AND o.Status <> 5"
    private val yearExpr = "CAST(strftime('%Y', o.OrderDate) AS INTEGER)"
    private val monthExpr = "CAST(strftime('%m', o.OrderDate) AS INTEGER)"
    private val dayExpr = "CAST(strftime('%d', o.OrderDate) AS INTEGER)"

    suspend fun monthly(year: Int, month: Int?): ReportSummary = Db.withConnection { conn ->
        val extra = if (month != null) " AND $monthExpr = ?" else ""
        val filter = "${baseWhere()} AND $yearExpr = ?$extra"
        val args = buildList { add(Session.userId); add(year); if (month != null) add(month) }
        val rows = groupRows(conn, filter, args, monthExpr) { monthNames[it - 1] }
        val period = if (month == null) "$year" else "${monthNames[month - 1]} $year"
        summary(conn, period, filter, args, rows)
    }

    suspend fun daily(year: Int, month: Int): ReportSummary = Db.withConnection { conn ->
        val filter = "${baseWhere()} AND $yearExpr = ? AND $monthExpr = ?"
        val args = listOf(Session.userId, year, month)
        val rows = groupRows(conn, filter, args, dayExpr) { "%02d %s".format(it, monthNames[month - 1]) }
        summary(conn, "${monthNames[month - 1]} $year", filter, args, rows)
    }

    suspend fun yearly(year: Int): ReportSummary = monthly(year, null)

    suspend fun byCustomer(): List<CustomerReportRow> = Db.withConnection { conn ->
        conn.prepareStatement(
            """
            SELECT o.CustomerId, c.Name,
                   COUNT(*) AS TotalOrders,
                   SUM(CASE WHEN o.Status NOT IN (2,3) THEN 1 ELSE 0 END) AS Pending,
                   SUM(CASE WHEN o.Status IN (2,3) THEN 1 ELSE 0 END) AS Delivered,
                   SUM(o.TotalAmount) AS TotalAmount, SUM(o.PaidAmount) AS Paid, SUM(o.RemainingAmount) AS Remaining
            FROM Orders o INNER JOIN Customers c ON c.Id = o.CustomerId
            ${baseWhere()}
            GROUP BY o.CustomerId, c.Name ORDER BY c.Name
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, Session.userId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        CustomerReportRow(
                            customerId = rs.getInt("CustomerId"), customerName = rs.getString("Name") ?: "",
                            totalOrders = rs.getInt("TotalOrders"), pendingOrders = rs.getInt("Pending"), deliveredOrders = rs.getInt("Delivered"),
                            totalAmount = rs.getDouble("TotalAmount"), paidAmount = rs.getDouble("Paid"), remainingAmount = rs.getDouble("Remaining"),
                        )
                    )
                }
            }
        }
    }

    private fun groupRows(conn: Connection, filter: String, args: List<Int>, groupExpr: String, label: (Int) -> String): List<ReportRow> {
        val sql = """
            SELECT $groupExpr AS Grp, COUNT(*) AS Cnt,
                   SUM(o.TotalAmount) AS Total, SUM(o.PaidAmount) AS Paid, SUM(o.RemainingAmount) AS Remaining
            FROM Orders o $filter GROUP BY $groupExpr ORDER BY $groupExpr
        """.trimIndent()
        return conn.prepareStatement(sql).use { ps ->
            args.forEachIndexed { i, a -> ps.setInt(i + 1, a) }
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        ReportRow(
                            label = label(rs.getInt("Grp")), orderCount = rs.getInt("Cnt"),
                            totalAmount = rs.getDouble("Total"), paidAmount = rs.getDouble("Paid"), remainingAmount = rs.getDouble("Remaining"),
                        )
                    )
                }
            }
        }
    }

    private fun summary(conn: Connection, period: String, filter: String, args: List<Int>, rows: List<ReportRow>): ReportSummary {
        val sql = """
            SELECT COUNT(*) AS Cnt, COALESCE(SUM(o.TotalAmount),0) AS Total, COALESCE(SUM(o.PaidAmount),0) AS Paid,
                   COALESCE(SUM(o.RemainingAmount),0) AS Remaining,
                   SUM(CASE WHEN o.Status NOT IN (2,3) THEN 1 ELSE 0 END) AS Pending,
                   SUM(CASE WHEN o.Status IN (2,3) THEN 1 ELSE 0 END) AS Delivered
            FROM Orders o $filter
        """.trimIndent()
        return conn.prepareStatement(sql).use { ps ->
            args.forEachIndexed { i, a -> ps.setInt(i + 1, a) }
            ps.executeQuery().use { rs ->
                rs.next()
                ReportSummary(
                    period = period, totalOrders = rs.getInt("Cnt"), totalAmount = rs.getDouble("Total"),
                    totalPaid = rs.getDouble("Paid"), totalRemaining = rs.getDouble("Remaining"),
                    pendingOrders = rs.getInt("Pending"), deliveredOrders = rs.getInt("Delivered"), rows = rows,
                )
            }
        }
    }
}
