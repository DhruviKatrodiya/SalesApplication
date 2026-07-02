package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.models.Customer
import com.salesapp.mobile.data.models.CustomerAdvance
import com.salesapp.mobile.data.models.CustomerSearchResult
import com.salesapp.mobile.data.models.Order
import com.salesapp.mobile.data.models.OrderSource
import com.salesapp.mobile.data.models.OrderStatus
import com.salesapp.mobile.data.models.Paged
import com.salesapp.mobile.data.models.PaymentStatus
import java.sql.Connection
import java.sql.PreparedStatement
import kotlin.math.max

/**
 * CRUD + profile aggregation for customers, mirroring CustomersController:
 *  - user-scoped, route join + validation,
 *  - details/advance reproduce OrderMath (overpayment → advance credit, cancelled orders' paid becomes advance).
 */
class CustomerRepository {

    suspend fun list(
        name: String? = null,
        phone: String? = null,
        routeId: Int? = null,
        active: String = "active",
        page: Int = 1,
        pageSize: Int = 500,
    ): Paged<Customer> = Db.withConnection { conn ->
        val uid = Session.userId
        val where = StringBuilder("WHERE c.UserId = ?")
        val args = mutableListOf<Any>(uid)
        if (active != "all") { where.append(" AND c.IsActive = ?"); args.add(if (active == "inactive") 0 else 1) }
        if (!name.isNullOrBlank()) { where.append(" AND c.Name LIKE ?"); args.add("%${name.trim()}%") }
        if (!phone.isNullOrBlank()) { where.append(" AND c.Phone LIKE ?"); args.add("%${phone.trim()}%") }
        if (routeId != null) { where.append(" AND c.RouteId = ?"); args.add(routeId) }

        val total = conn.prepareStatement("SELECT COUNT(*) FROM Customers c $where").use { ps ->
            bind(ps, args); ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }
        val safePage = if (page < 1) 1 else page
        val sql = """
            SELECT c.Id, c.Name, c.Phone, c.Email, c.Address, c.RouteId, r.Name AS RouteName, c.CreatedAt, c.IsActive
            FROM Customers c
            LEFT JOIN Routes r ON r.Id = c.RouteId
            $where
            ORDER BY c.CreatedAt DESC, c.Id DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """.trimIndent()
        val items = conn.prepareStatement(sql).use { ps ->
            val n = bind(ps, args); ps.setInt(n, (safePage - 1) * pageSize); ps.setInt(n + 1, pageSize)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(mapCustomer(rs)) } }
        }
        Paged(items, total, safePage, pageSize)
    }

    private fun routeOwned(conn: Connection, routeId: Int): Boolean =
        conn.prepareStatement("SELECT 1 FROM Routes WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setInt(1, routeId); ps.setInt(2, Session.userId); ps.executeQuery().use { it.next() }
        }

    suspend fun create(
        name: String, phone: String?, email: String?, address: String?, routeId: Int?,
    ): Result<Int> = Db.withConnection { conn ->
        if (routeId != null && !routeOwned(conn, routeId))
            return@withConnection Result.failure(IllegalArgumentException("Route not found."))
        val id = conn.prepareStatement(
            "INSERT INTO Customers (UserId, Name, Phone, Email, Address, RouteId, CreatedAt, IsActive) " +
                "OUTPUT INSERTED.Id VALUES (?, ?, ?, ?, ?, ?, SYSUTCDATETIME(), 1)"
        ).use { ps ->
            ps.setInt(1, Session.userId); ps.setString(2, name.trim())
            ps.setString(3, phone?.trim()); ps.setString(4, email?.trim()); ps.setString(5, address?.trim())
            if (routeId != null) ps.setInt(6, routeId) else ps.setNull(6, java.sql.Types.INTEGER)
            ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }
        Result.success(id)
    }

    suspend fun update(
        id: Int, name: String, phone: String?, email: String?, address: String?, routeId: Int?,
    ): Result<Unit> = Db.withConnection { conn ->
        if (routeId != null && !routeOwned(conn, routeId))
            return@withConnection Result.failure(IllegalArgumentException("Route not found."))
        conn.prepareStatement(
            "UPDATE Customers SET Name = ?, Phone = ?, Email = ?, Address = ?, RouteId = ? WHERE Id = ? AND UserId = ?"
        ).use { ps ->
            ps.setString(1, name.trim()); ps.setString(2, phone?.trim())
            ps.setString(3, email?.trim()); ps.setString(4, address?.trim())
            if (routeId != null) ps.setInt(5, routeId) else ps.setNull(5, java.sql.Types.INTEGER)
            ps.setInt(6, id); ps.setInt(7, Session.userId); ps.executeUpdate()
        }
        Result.success(Unit)
    }

    suspend fun deactivate(id: Int) = setActive(id, false)
    suspend fun activate(id: Int) = setActive(id, true)

    private suspend fun setActive(id: Int, active: Boolean): Boolean = Db.withConnection { conn ->
        conn.prepareStatement("UPDATE Customers SET IsActive = ? WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setBoolean(1, active); ps.setInt(2, id); ps.setInt(3, Session.userId); ps.executeUpdate() > 0
        }
    }

    /** Full profile with financial aggregation (mirrors BuildDetails + OrderMath). */
    suspend fun details(customerId: Int): CustomerSearchResult? = Db.withConnection { conn ->
        val customer = conn.prepareStatement(
            """
            SELECT c.Id, c.Name, c.Phone, c.Email, c.Address, c.RouteId, r.Name AS RouteName, c.CreatedAt, c.IsActive
            FROM Customers c LEFT JOIN Routes r ON r.Id = c.RouteId
            WHERE c.Id = ? AND c.UserId = ?
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, customerId); ps.setInt(2, Session.userId)
            ps.executeQuery().use { if (it.next()) mapCustomer(it) else null }
        } ?: return@withConnection null

        buildDetails(conn, customer)
    }

    suspend fun search(query: String): List<CustomerSearchResult> = Db.withConnection { conn ->
        val term = "%${query.trim()}%"
        val customers = conn.prepareStatement(
            """
            SELECT c.Id, c.Name, c.Phone, c.Email, c.Address, c.RouteId, r.Name AS RouteName, c.CreatedAt, c.IsActive
            FROM Customers c LEFT JOIN Routes r ON r.Id = c.RouteId
            WHERE c.UserId = ? AND (c.Name LIKE ? OR c.Phone LIKE ? OR c.Email LIKE ?)
            ORDER BY c.CreatedAt DESC, c.Id DESC
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, Session.userId); ps.setString(2, term); ps.setString(3, term); ps.setString(4, term)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(mapCustomer(rs)) } }
        }
        customers.map { buildDetails(conn, it) }
    }

    suspend fun advance(customerId: Int): CustomerAdvance = Db.withConnection { conn ->
        val orders = loadCustomerOrders(conn, customerId)
        CustomerAdvance(orders.sumOf { overpaidOn(it) })
    }

    // ---- internals ----

    private fun buildDetails(conn: Connection, c: Customer): CustomerSearchResult {
        val orders = loadCustomerOrders(conn, c.id)
        val active = orders.filter { it.status != OrderStatus.Cancelled }

        val totalOrdered = active.sumOf { it.totalAmount }
        val totalPaid = orders.sumOf { it.paidAmount }
        val advanceBalance = orders.sumOf { overpaidOn(it) }
        val orderPayments = totalPaid - advanceBalance
        val totalRemaining = active.sumOf { max(0.0, it.totalAmount - it.paidAmount) }
        val advanceUsed = advanceUsed(conn, c.id)

        val overall = when {
            totalPaid <= 0 -> "Pending"
            totalRemaining <= 0 -> "Paid"
            else -> "Advance"
        }
        val pending = active.count { it.status != OrderStatus.Delivered && it.status != OrderStatus.Completed }
        val delivered = active.count { it.status == OrderStatus.Delivered || it.status == OrderStatus.Completed }

        return CustomerSearchResult(
            customer = c,
            totalOrdered = totalOrdered, totalPaid = totalPaid, totalRemaining = totalRemaining,
            orderPayments = orderPayments, advanceBalance = advanceBalance, advanceUsed = advanceUsed,
            overallPaymentStatus = overall, pendingOrders = pending, deliveredOrders = delivered,
            orders = active,
        )
    }

    /** Active orders (any status) for a customer — header fields only. */
    private fun loadCustomerOrders(conn: Connection, customerId: Int): List<Order> =
        conn.prepareStatement(
            """
            SELECT Id, OrderNumber, OrderDate, DeliveryDate, Status, PaymentStatus, Source,
                   TotalAmount, PaidAmount, RemainingAmount, Notes
            FROM Orders WHERE CustomerId = ? AND IsActive = 1
            ORDER BY OrderDate DESC
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, customerId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        Order(
                            id = rs.getInt("Id"),
                            orderNumber = rs.getString("OrderNumber") ?: "",
                            customerId = customerId,
                            customerName = "",
                            orderDate = rs.getString("OrderDate"),
                            deliveryDate = rs.getString("DeliveryDate"),
                            status = OrderStatus.from(rs.getInt("Status")),
                            paymentStatus = PaymentStatus.from(rs.getInt("PaymentStatus")),
                            source = OrderSource.from(rs.getInt("Source")),
                            totalAmount = rs.getBigDecimal("TotalAmount")?.toDouble() ?: 0.0,
                            paidAmount = rs.getBigDecimal("PaidAmount")?.toDouble() ?: 0.0,
                            remainingAmount = rs.getBigDecimal("RemainingAmount")?.toDouble() ?: 0.0,
                            notes = rs.getString("Notes"),
                        )
                    )
                }
            }
        }

    private fun advanceUsed(conn: Connection, customerId: Int): Double =
        conn.prepareStatement(
            "SELECT COALESCE(SUM(p.Amount), 0) FROM Payments p " +
                "INNER JOIN Orders o ON o.Id = p.OrderId " +
                "WHERE o.CustomerId = ? AND o.IsActive = 1 AND p.Method = 'Advance'"
        ).use { ps ->
            ps.setInt(1, customerId)
            ps.executeQuery().use { if (it.next()) it.getBigDecimal(1)?.toDouble() ?: 0.0 else 0.0 }
        }

    /** OrderMath.OverpaidOn: cancelled → all paid becomes advance; else the amount over the total. */
    private fun overpaidOn(o: Order): Double =
        if (o.status == OrderStatus.Cancelled) max(0.0, o.paidAmount)
        else max(0.0, o.paidAmount - o.totalAmount)

    private fun mapCustomer(rs: java.sql.ResultSet): Customer {
        val routeId = rs.getInt("RouteId").let { if (rs.wasNull()) null else it }
        return Customer(
            id = rs.getInt("Id"),
            name = rs.getString("Name") ?: "",
            phone = rs.getString("Phone"),
            email = rs.getString("Email"),
            address = rs.getString("Address"),
            routeId = routeId,
            routeName = rs.getString("RouteName"),
            createdAt = rs.getString("CreatedAt"),
            isActive = rs.getBoolean("IsActive"),
        )
    }

    private fun bind(ps: PreparedStatement, args: List<Any>): Int {
        args.forEachIndexed { i, a -> when (a) {
            is Int -> ps.setInt(i + 1, a); is String -> ps.setString(i + 1, a); else -> ps.setObject(i + 1, a)
        } }
        return args.size + 1
    }
}
