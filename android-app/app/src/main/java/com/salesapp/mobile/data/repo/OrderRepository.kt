package com.salesapp.mobile.data.repo

import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.models.Order
import com.salesapp.mobile.data.models.OrderItem
import com.salesapp.mobile.data.models.OrderSource
import com.salesapp.mobile.data.models.OrderStatus
import com.salesapp.mobile.data.models.Paged
import com.salesapp.mobile.data.models.PaymentStatus
import com.salesapp.mobile.data.models.ReceivedStatus
import java.sql.Connection
import java.sql.PreparedStatement

/** One requested order line. */
data class OrderLine(val itemId: Int, val quantity: Int, val unitPrice: Double? = null)

/**
 * Orders + line items (local SQLite), mirroring OrdersController. Inventory orders draw from godown
 * stock (FIFO); Dispatch orders draw from the chosen truck's stock. Stock actions run in a transaction.
 */
class OrderRepository {

    suspend fun list(
        orderNumber: String? = null,
        customer: String? = null,
        orderDate: String? = null,
        status: OrderStatus? = null,
        paymentStatus: PaymentStatus? = null,
        customerId: Int? = null,
        active: String = "active",
        page: Int = 1,
        pageSize: Int = 500,
    ): Paged<Order> = Db.withConnection { conn ->
        val uid = Session.userId
        val where = StringBuilder("WHERE o.SalesmanId = ?")
        val args = mutableListOf<Any>(uid)
        if (active != "all") { where.append(" AND o.IsActive = ?"); args.add(if (active == "inactive") 0 else 1) }
        if (customerId != null) { where.append(" AND o.CustomerId = ?"); args.add(customerId) }
        if (!orderNumber.isNullOrBlank()) { where.append(" AND o.OrderNumber LIKE ?"); args.add("%${orderNumber.trim()}%") }
        if (!customer.isNullOrBlank()) { where.append(" AND c.Name LIKE ?"); args.add("%${customer.trim()}%") }
        if (!orderDate.isNullOrBlank()) { where.append(" AND date(o.OrderDate) = ?"); args.add(orderDate) }
        if (status != null) { where.append(" AND o.Status = ?"); args.add(status.value) }
        if (paymentStatus != null) { where.append(" AND o.PaymentStatus = ?"); args.add(paymentStatus.value) }

        val joins = "INNER JOIN Customers c ON c.Id = o.CustomerId LEFT JOIN Trucks t ON t.Id = o.TruckId"
        val total = conn.prepareStatement("SELECT COUNT(*) FROM Orders o $joins $where").use { ps ->
            bind(ps, args); ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }
        val safePage = if (page < 1) 1 else page
        val sql = """
            SELECT o.Id, o.OrderNumber, o.CustomerId, c.Name AS CustomerName, o.OrderDate, o.DeliveryDate,
                   o.Status, o.PaymentStatus, o.Source, o.TotalAmount, o.PaidAmount, o.RemainingAmount,
                   o.Notes, o.TruckId, t.Name AS TruckName, o.IsActive
            FROM Orders o $joins $where ORDER BY o.CreatedAt DESC, o.Id DESC LIMIT ?, ?
        """.trimIndent()
        val items = conn.prepareStatement(sql).use { ps ->
            val n = bind(ps, args); ps.setInt(n, (safePage - 1) * pageSize); ps.setInt(n + 1, pageSize)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(mapOrder(rs)) } }
        }
        Paged(items, total, safePage, pageSize)
    }

    suspend fun getItems(orderId: Int): List<OrderItem> = Db.withConnection { conn -> loadItems(conn, orderId) }

    suspend fun create(
        customerId: Int, source: OrderSource, truckId: Int?,
        orderDate: String?, deliveryDate: String?, notes: String?, lines: List<OrderLine>,
    ): Result<Int> = Db.withConnection { conn ->
        val uid = Session.userId
        if (!owns(conn, "Customers", customerId, uid)) return@withConnection Result.failure(IllegalArgumentException("Customer not found."))
        if (lines.isEmpty()) return@withConnection Result.failure(IllegalArgumentException("At least one order item is required."))
        conn.autoCommit = false
        try {
            if (source == OrderSource.Dispatch && truckId == null) throw IllegalArgumentException("Select a truck for a dispatch order.")
            if (source == OrderSource.Dispatch && !owns(conn, "Trucks", truckId!!, uid)) throw IllegalArgumentException("Truck not found.")
            for (line in lines) {
                val item = itemRow(conn, line.itemId, uid) ?: throw IllegalArgumentException("Item ${line.itemId} not found.")
                if (line.quantity <= 0) throw IllegalArgumentException("Quantity for '${item.name}' must be greater than zero.")
                val available = if (source == OrderSource.Dispatch) truckQty(conn, truckId!!, line.itemId) else item.stock
                if (available < line.quantity) throw IllegalStateException(
                    "Insufficient ${if (source == OrderSource.Dispatch) "truck" else "inventory"} stock for '${item.name}'. Available: $available, requested: ${line.quantity}.")
            }
            val total = Sql.round2(lines.sumOf { (it.unitPrice ?: itemRow(conn, it.itemId, uid)!!.unitPrice) * it.quantity })
            val orderNumber = generateOrderNumber(conn)
            val orderDateExpr = if (orderDate.isNullOrBlank()) "datetime('now')" else "?"
            conn.prepareStatement(
                "INSERT INTO Orders (OrderNumber, CustomerId, SalesmanId, TruckId, OrderDate, DeliveryDate, Notes, " +
                    "Status, PaymentStatus, Source, TotalAmount, PaidAmount, RemainingAmount, CreatedAt, IsActive) " +
                    "VALUES (?, ?, ?, ?, $orderDateExpr, ?, ?, 0, 0, ?, ?, 0, ?, datetime('now'), 1)"
            ).use { ps ->
                var i = 1
                ps.setString(i++, orderNumber); ps.setInt(i++, customerId); ps.setInt(i++, uid)
                if (truckId != null && source == OrderSource.Dispatch) ps.setInt(i++, truckId) else ps.setNull(i++, java.sql.Types.INTEGER)
                if (!orderDate.isNullOrBlank()) ps.setString(i++, orderDate)
                if (deliveryDate.isNullOrBlank()) ps.setNull(i++, java.sql.Types.VARCHAR) else ps.setString(i++, deliveryDate)
                ps.setString(i++, notes); ps.setInt(i++, source.value); ps.setDouble(i++, total); ps.setDouble(i, total)
                ps.executeUpdate()
            }
            val orderId = Sql.lastInsertId(conn)
            insertLines(conn, uid, orderId, source, truckId, lines)
            conn.commit()
            Result.success(orderId)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    suspend fun update(
        id: Int, customerId: Int, source: OrderSource, truckId: Int?,
        orderDate: String?, deliveryDate: String?, notes: String?, status: OrderStatus?, lines: List<OrderLine>,
    ): Result<Unit> = Db.withConnection { conn ->
        val uid = Session.userId
        val current = orderHeader(conn, id) ?: return@withConnection Result.failure(IllegalArgumentException("Order not found."))
        if (current.salesmanId != uid) return@withConnection Result.failure(IllegalStateException("You can only manage your own orders."))
        if (current.status == OrderStatus.Cancelled) return@withConnection Result.failure(IllegalStateException("This order is cancelled and can no longer be edited."))
        if (current.status == OrderStatus.Completed) return@withConnection Result.failure(IllegalStateException("This order is completed and can no longer be edited."))
        if (lines.isEmpty()) return@withConnection Result.failure(IllegalArgumentException("At least one order item is required."))
        if (!owns(conn, "Customers", customerId, uid)) return@withConnection Result.failure(IllegalArgumentException("Customer not found."))
        if (source == OrderSource.Dispatch && truckId == null) return@withConnection Result.failure(IllegalArgumentException("Select a truck for a dispatch order."))
        if (source == OrderSource.Dispatch && !owns(conn, "Trucks", truckId!!, uid)) return@withConnection Result.failure(IllegalArgumentException("Truck not found."))

        conn.autoCommit = false
        try {
            val oldLines = loadItems(conn, id)
            if (current.source == OrderSource.Inventory) InventoryOps.reverse(conn, "Order", id)
            else for (ol in oldLines) {
                if (current.truckId != null) addTruckStock(conn, current.truckId, ol.itemId, ol.quantity)
                InventoryOps.adjustDispatchStock(conn, ol.itemId, ol.quantity)
            }
            for (line in lines) {
                val item = itemRow(conn, line.itemId, uid) ?: throw IllegalArgumentException("Item ${line.itemId} not found.")
                if (line.quantity <= 0) throw IllegalArgumentException("Quantity for '${item.name}' must be greater than zero.")
                val available = if (source == OrderSource.Dispatch) truckQty(conn, truckId!!, line.itemId) else item.stock
                if (available < line.quantity) throw IllegalStateException(
                    "Insufficient ${if (source == OrderSource.Dispatch) "truck" else "inventory"} stock for '${item.name}'. Available: $available, requested: ${line.quantity}.")
            }
            conn.prepareStatement("DELETE FROM OrderItems WHERE OrderId = ?").use { ps -> ps.setInt(1, id); ps.executeUpdate() }
            val total = Sql.round2(lines.sumOf { (it.unitPrice ?: itemRow(conn, it.itemId, uid)!!.unitPrice) * it.quantity })
            val setDate = if (orderDate.isNullOrBlank()) "" else "OrderDate = ?, "
            conn.prepareStatement(
                "UPDATE Orders SET CustomerId = ?, ${setDate}DeliveryDate = ?, Notes = ?, Status = ?, Source = ?, TruckId = ?, TotalAmount = ? WHERE Id = ?"
            ).use { ps ->
                var i = 1
                ps.setInt(i++, customerId)
                if (!orderDate.isNullOrBlank()) ps.setString(i++, orderDate)
                if (deliveryDate.isNullOrBlank()) ps.setNull(i++, java.sql.Types.VARCHAR) else ps.setString(i++, deliveryDate)
                ps.setString(i++, notes); ps.setInt(i++, (status ?: current.status).value); ps.setInt(i++, source.value)
                if (source == OrderSource.Dispatch) ps.setInt(i++, truckId!!) else ps.setNull(i++, java.sql.Types.INTEGER)
                ps.setDouble(i++, total); ps.setInt(i, id)
                ps.executeUpdate()
            }
            insertLines(conn, uid, id, source, truckId, lines)
            OrderMath.recalculate(conn, id)
            conn.commit()
            Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    suspend fun updateStatus(id: Int, status: OrderStatus): Result<Unit> = Db.withConnection { conn ->
        val h = orderHeader(conn, id) ?: return@withConnection Result.failure(IllegalArgumentException("Order not found."))
        if (h.salesmanId != Session.userId) return@withConnection Result.failure(IllegalStateException("You can only manage your own orders."))
        if (h.status == OrderStatus.Cancelled) return@withConnection Result.failure(IllegalStateException("This order is cancelled."))
        if (h.status == OrderStatus.Completed) return@withConnection Result.failure(IllegalStateException("This order is completed."))
        conn.prepareStatement("UPDATE Orders SET Status = ? WHERE Id = ?").use { ps -> ps.setInt(1, status.value); ps.setInt(2, id); ps.executeUpdate() }
        OrderMath.recalculate(conn, id)
        Result.success(Unit)
    }

    suspend fun updateDeliveryDate(id: Int, deliveryDate: String?): Result<Unit> = Db.withConnection { conn ->
        if (!ownsOrder(conn, id)) return@withConnection Result.failure(IllegalStateException("You can only manage your own orders."))
        conn.prepareStatement("UPDATE Orders SET DeliveryDate = ? WHERE Id = ?").use { ps ->
            if (deliveryDate.isNullOrBlank()) ps.setNull(1, java.sql.Types.VARCHAR) else ps.setString(1, deliveryDate)
            ps.setInt(2, id); ps.executeUpdate()
        }
        Result.success(Unit)
    }

    suspend fun updateReceivedStatus(orderId: Int, orderItemId: Int, received: ReceivedStatus): Result<Unit> = Db.withConnection { conn ->
        if (!ownsOrder(conn, orderId)) return@withConnection Result.failure(IllegalStateException("You can only manage your own orders."))
        conn.prepareStatement("UPDATE OrderItems SET ReceivedStatus = ? WHERE Id = ? AND OrderId = ?").use { ps ->
            ps.setInt(1, received.value); ps.setInt(2, orderItemId); ps.setInt(3, orderId); ps.executeUpdate()
        }
        Result.success(Unit)
    }

    suspend fun cancel(id: Int): Result<Unit> = Db.withConnection { conn ->
        val h = orderHeader(conn, id) ?: return@withConnection Result.failure(IllegalArgumentException("Order not found."))
        if (h.salesmanId != Session.userId) return@withConnection Result.failure(IllegalStateException("You can only manage your own orders."))
        if (h.status == OrderStatus.Cancelled) return@withConnection Result.failure(IllegalStateException("This order is already cancelled."))
        conn.autoCommit = false
        try {
            val lines = loadItems(conn, id)
            if (h.source == OrderSource.Inventory) InventoryOps.reverse(conn, "Order", id)
            else for (line in lines) {
                InventoryOps.adjustDispatchStock(conn, line.itemId, line.quantity)
                if (h.truckId != null) addTruckStock(conn, h.truckId, line.itemId, line.quantity)
            }
            conn.prepareStatement("UPDATE Orders SET Status = ?, RemainingAmount = 0 WHERE Id = ?").use { ps ->
                ps.setInt(1, OrderStatus.Cancelled.value); ps.setInt(2, id); ps.executeUpdate()
            }
            conn.commit()
            Result.success(Unit)
        } catch (e: Exception) { conn.rollback(); Result.failure(e) } finally { conn.autoCommit = true }
    }

    suspend fun deactivate(id: Int): Boolean = setActive(id, false)
    suspend fun activate(id: Int): Boolean = setActive(id, true)

    private suspend fun setActive(id: Int, active: Boolean): Boolean = Db.withConnection { conn ->
        conn.prepareStatement("UPDATE Orders SET IsActive = ? WHERE Id = ? AND SalesmanId = ?").use { ps ->
            ps.setBoolean(1, active); ps.setInt(2, id); ps.setInt(3, Session.userId); ps.executeUpdate() > 0
        }
    }

    // ---- internals ----

    private data class ItemRow(val name: String, val stock: Int, val unitPrice: Double)
    private fun itemRow(conn: Connection, itemId: Int, uid: Int): ItemRow? =
        conn.prepareStatement("SELECT Name, StockQuantity, UnitPrice FROM Items WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setInt(1, itemId); ps.setInt(2, uid)
            ps.executeQuery().use { if (it.next()) ItemRow(it.getString(1) ?: "", it.getInt(2), it.getDouble(3)) else null }
        }

    private data class Header(val salesmanId: Int, val status: OrderStatus, val source: OrderSource, val truckId: Int?, val orderNumber: String)
    private fun orderHeader(conn: Connection, id: Int): Header? =
        conn.prepareStatement("SELECT SalesmanId, Status, Source, TruckId, OrderNumber FROM Orders WHERE Id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use {
                if (!it.next()) null
                else Header(it.getInt(1), OrderStatus.from(it.getInt(2)), OrderSource.from(it.getInt(3)),
                    it.getInt(4).let { v -> if (it.wasNull()) null else v }, it.getString(5) ?: "")
            }
        }

    private fun ownsOrder(conn: Connection, id: Int): Boolean =
        conn.prepareStatement("SELECT 1 FROM Orders WHERE Id = ? AND SalesmanId = ?").use { ps ->
            ps.setInt(1, id); ps.setInt(2, Session.userId); ps.executeQuery().use { it.next() }
        }

    private fun owns(conn: Connection, table: String, id: Int, uid: Int): Boolean =
        conn.prepareStatement("SELECT 1 FROM $table WHERE Id = ? AND UserId = ?").use { ps ->
            ps.setInt(1, id); ps.setInt(2, uid); ps.executeQuery().use { it.next() }
        }

    private fun truckQty(conn: Connection, truckId: Int, itemId: Int): Int =
        conn.prepareStatement("SELECT COALESCE(Quantity, 0) FROM TruckStocks WHERE TruckId = ? AND ItemId = ?").use { ps ->
            ps.setInt(1, truckId); ps.setInt(2, itemId); ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }

    private fun addTruckStock(conn: Connection, truckId: Int, itemId: Int, delta: Int) {
        val updated = conn.prepareStatement("UPDATE TruckStocks SET Quantity = Quantity + ? WHERE TruckId = ? AND ItemId = ?").use { ps ->
            ps.setInt(1, delta); ps.setInt(2, truckId); ps.setInt(3, itemId); ps.executeUpdate()
        }
        if (updated == 0 && delta != 0) conn.prepareStatement("INSERT INTO TruckStocks (TruckId, ItemId, Quantity) VALUES (?, ?, ?)").use { ps ->
            ps.setInt(1, truckId); ps.setInt(2, itemId); ps.setInt(3, delta); ps.executeUpdate()
        }
    }

    private fun insertLines(conn: Connection, uid: Int, orderId: Int, source: OrderSource, truckId: Int?, lines: List<OrderLine>) {
        for (line in lines) {
            val item = itemRow(conn, line.itemId, uid)!!
            val price = line.unitPrice ?: item.unitPrice
            conn.prepareStatement(
                "INSERT INTO OrderItems (OrderId, ItemId, Quantity, UnitPrice, LineTotal, ReceivedStatus) VALUES (?, ?, ?, ?, ?, 0)"
            ).use { ps ->
                ps.setInt(1, orderId); ps.setInt(2, line.itemId); ps.setInt(3, line.quantity)
                ps.setDouble(4, price); ps.setDouble(5, Sql.round2(price * line.quantity)); ps.executeUpdate()
            }
            if (source == OrderSource.Dispatch) {
                addTruckStock(conn, truckId!!, line.itemId, -line.quantity)
                InventoryOps.adjustDispatchStock(conn, line.itemId, -line.quantity)
            } else {
                InventoryOps.consumeFifo(conn, line.itemId, line.quantity, "Order", orderId, orderNumberOf(conn, orderId))
            }
        }
    }

    private fun orderNumberOf(conn: Connection, orderId: Int): String =
        conn.prepareStatement("SELECT OrderNumber FROM Orders WHERE Id = ?").use { ps ->
            ps.setInt(1, orderId); ps.executeQuery().use { if (it.next()) it.getString(1) ?: "" else "" }
        }

    private fun generateOrderNumber(conn: Connection): String {
        var seq = conn.prepareStatement(
            "SELECT COUNT(*) FROM Orders WHERE strftime('%Y%m', CreatedAt) = strftime('%Y%m', 'now')"
        ).use { ps -> ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 } } + 1
        val datePart = conn.prepareStatement("SELECT strftime('%y-%m-%d', 'now')").use { ps ->
            ps.executeQuery().use { if (it.next()) it.getString(1) else "" }
        }
        fun format(s: Int) = "ORD-$datePart-${s.toString().padStart(6, '0')}"
        var number = format(seq)
        while (numberTaken(conn, number)) { seq++; number = format(seq) }
        return number
    }

    private fun numberTaken(conn: Connection, number: String): Boolean =
        conn.prepareStatement("SELECT 1 FROM Orders WHERE OrderNumber = ?").use { ps ->
            ps.setString(1, number); ps.executeQuery().use { it.next() }
        }

    private fun loadItems(conn: Connection, orderId: Int): List<OrderItem> =
        conn.prepareStatement(
            """
            SELECT oi.Id, oi.ItemId, i.Name, oi.Quantity, oi.UnitPrice, oi.LineTotal, oi.ReceivedStatus
            FROM OrderItems oi INNER JOIN Items i ON i.Id = oi.ItemId WHERE oi.OrderId = ? ORDER BY oi.Id
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, orderId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        OrderItem(
                            id = rs.getInt("Id"), itemId = rs.getInt("ItemId"), itemName = rs.getString("Name") ?: "",
                            quantity = rs.getInt("Quantity"), unitPrice = rs.getDouble("UnitPrice"), lineTotal = rs.getDouble("LineTotal"),
                            receivedStatus = ReceivedStatus.from(rs.getInt("ReceivedStatus")),
                        )
                    )
                }
            }
        }

    private fun mapOrder(rs: java.sql.ResultSet): Order {
        val truckId = rs.getInt("TruckId").let { if (rs.wasNull()) null else it }
        return Order(
            id = rs.getInt("Id"), orderNumber = rs.getString("OrderNumber") ?: "", customerId = rs.getInt("CustomerId"),
            customerName = rs.getString("CustomerName") ?: "", orderDate = rs.getString("OrderDate"), deliveryDate = rs.getString("DeliveryDate"),
            status = OrderStatus.from(rs.getInt("Status")), paymentStatus = PaymentStatus.from(rs.getInt("PaymentStatus")),
            source = OrderSource.from(rs.getInt("Source")), totalAmount = rs.getDouble("TotalAmount"),
            paidAmount = rs.getDouble("PaidAmount"), remainingAmount = rs.getDouble("RemainingAmount"),
            notes = rs.getString("Notes"), truckId = truckId, truckName = rs.getString("TruckName"), isActive = rs.getBoolean("IsActive"),
        )
    }

    private fun bind(ps: PreparedStatement, args: List<Any>): Int {
        args.forEachIndexed { i, a -> when (a) {
            is Int -> ps.setInt(i + 1, a); is String -> ps.setString(i + 1, a); else -> ps.setObject(i + 1, a)
        } }
        return args.size + 1
    }
}
