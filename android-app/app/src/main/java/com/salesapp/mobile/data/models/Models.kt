package com.salesapp.mobile.data.models

/**
 * Kotlin mirror of the SalesAppDb schema (EF-created) and the enums the backend uses.
 * Numeric enum values MUST match the .NET side exactly — they are stored as ints in SQL Server.
 */

enum class OrderStatus(val value: Int, val label: String) {
    Pending(0, "Pending"),
    Dispatched(1, "Dispatched"),
    Delivered(2, "Delivered"),
    Completed(3, "Completed"),
    Remaining(4, "Remaining"),
    Cancelled(5, "Cancelled");

    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: Pending }
}

enum class PaymentStatus(val value: Int, val label: String) {
    Pending(0, "Pending"),
    Advance(1, "Advance"),
    Paid(2, "Paid"),
    Partial(3, "Partial");

    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: Pending }
}

enum class ReceivedStatus(val value: Int, val label: String) {
    Pending(0, "Pending"),
    Remaining(1, "Remaining"),
    Completed(2, "Completed");

    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: Pending }
}

enum class OrderSource(val value: Int, val label: String) {
    Inventory(0, "Inventory"),
    Dispatch(1, "Dispatch");

    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: Inventory }
}

enum class StockRequestStatus(val value: Int, val label: String) {
    Pending(0, "Pending"),
    Fulfilled(1, "Fulfilled"),
    Cancelled(2, "Cancelled"),
    Done(3, "Done");

    companion object { fun from(v: Int) = entries.firstOrNull { it.value == v } ?: Pending }
}

// ---- Auth / user ----
data class User(
    val id: Int,
    val fullName: String,
    val email: String,
    val phone: String? = null,
    val profileImagePath: String? = null,
)

// ---- Catalog ----
data class Category(
    val id: Int,
    val name: String,
    val description: String? = null,
    val subCategoryCount: Int = 0,
    val isActive: Boolean = true,
)

data class SubCategory(
    val id: Int,
    val categoryId: Int,
    val categoryName: String,
    val name: String,
    val description: String? = null,
    val itemCount: Int = 0,
    val isActive: Boolean = true,
)

data class Item(
    val id: Int,
    val subCategoryId: Int,
    val subCategoryName: String,
    val categoryName: String,
    val name: String,
    val sku: String? = null,
    val unit: String? = null,
    val stockQuantity: Int = 0,
    val dispatchStock: Int = 0,
    val unitPrice: Double = 0.0,
    val isActive: Boolean = true,
)

data class InventoryBatch(
    val id: Int,
    val initialQuantity: Int,
    val remaining: Int,
    val purchasePrice: Double,
    val createdAt: String? = null,
    val sourceRequestNumber: String? = null,
)

data class ItemPriceHistory(
    val itemId: Int,
    val name: String,
    val stockQuantity: Int,
    val oldestPrice: Double,
    val latestPrice: Double,
    val avgCost: Double,
    val stockValue: Double,
    val batches: List<InventoryBatch>,
)

data class InventoryMovement(
    val id: Int,
    val type: String,            // "IN" | "OUT"
    val quantity: Int,
    val unitCost: Double,
    val totalCost: Double,
    val refType: String,
    val source: String? = null,
    val reversed: Boolean = false,
    val createdAt: String? = null,
)

// ---- Routes / customers ----
data class DeliveryRoute(
    val id: Int,
    val name: String,
    val description: String? = null,
    val customerCount: Int = 0,
    val isActive: Boolean = true,
)

data class Customer(
    val id: Int,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val routeId: Int? = null,
    val routeName: String? = null,
    val createdAt: String? = null,
    val isActive: Boolean = true,
)

// ---- Orders ----
data class OrderItem(
    val id: Int,
    val itemId: Int,
    val itemName: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double,
    val receivedStatus: ReceivedStatus = ReceivedStatus.Pending,
)

data class Order(
    val id: Int,
    val orderNumber: String,
    val customerId: Int,
    val customerName: String,
    val orderDate: String? = null,
    val deliveryDate: String? = null,
    val status: OrderStatus = OrderStatus.Pending,
    val paymentStatus: PaymentStatus = PaymentStatus.Pending,
    val source: OrderSource = OrderSource.Inventory,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val notes: String? = null,
    val truckId: Int? = null,
    val truckName: String? = null,
    val isActive: Boolean = true,
    val items: List<OrderItem> = emptyList(),
)

data class Payment(
    val id: Int,
    val orderId: Int,
    val amount: Double,
    val paymentDate: String? = null,
    val method: String? = null,
    val note: String? = null,
)

/** Full customer profile powering the "search → see everything" view. */
data class CustomerSearchResult(
    val customer: Customer,
    val totalOrdered: Double,
    val totalPaid: Double,
    val totalRemaining: Double,
    val orderPayments: Double,
    val advanceBalance: Double,
    val advanceUsed: Double,
    val overallPaymentStatus: String,
    val pendingOrders: Int,
    val deliveredOrders: Int,
    val orders: List<Order>,
)

data class CustomerAdvance(val advanceBalance: Double)

// ---- Trucks / dispatch ----
data class TruckStockItem(
    val itemId: Int,
    val name: String,
    val sku: String? = null,
    val unit: String? = null,
    val quantity: Int,
    val unitPrice: Double,
)

data class Truck(
    val id: Int,
    val name: String,
    val createdAt: String? = null,
    val itemCount: Int = 0,
    val totalUnits: Int = 0,
    val isActive: Boolean = true,
)

data class DispatchLine(val itemId: Int, val itemName: String, val quantity: Int)

data class Dispatch(
    val id: Int,
    val dispatchDate: String? = null,
    val truckLabel: String,
    val notes: String? = null,
    val items: List<DispatchLine> = emptyList(),
    val isActive: Boolean = true,
)

// ---- Stock requests ("My Orders") ----
data class StockRequestLine(
    val itemId: Int,
    val itemName: String,
    val quantity: Int,
    val currentStock: Int,
    val unitPrice: Double,
    val lineTotal: Double,
)

data class StockRequest(
    val id: Int,
    val requestNumber: String,
    val createdAt: String? = null,
    val status: StockRequestStatus = StockRequestStatus.Pending,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val paymentStatus: PaymentStatus = PaymentStatus.Pending,
    val notes: String? = null,
    val items: List<StockRequestLine> = emptyList(),
    val isActive: Boolean = true,
)

data class StockRequestPayment(
    val id: Int,
    val stockRequestId: Int,
    val amount: Double,
    val paymentDate: String? = null,
    val method: String? = null,
    val note: String? = null,
)

// ---- Reports ----
data class ReportRow(
    val label: String,
    val orderCount: Int,
    val totalAmount: Double,
    val paidAmount: Double,
    val remainingAmount: Double,
)

data class ReportSummary(
    val period: String,
    val totalOrders: Int,
    val totalAmount: Double,
    val totalPaid: Double,
    val totalRemaining: Double,
    val pendingOrders: Int,
    val deliveredOrders: Int,
    val rows: List<ReportRow>,
)

data class CustomerReportRow(
    val customerId: Int,
    val customerName: String,
    val totalOrders: Int,
    val pendingOrders: Int,
    val deliveredOrders: Int,
    val totalAmount: Double,
    val paidAmount: Double,
    val remainingAmount: Double,
)

/** A single row of a paginated query plus the total count for the paginator. */
data class Paged<T>(val items: List<T>, val total: Int, val page: Int, val pageSize: Int)
