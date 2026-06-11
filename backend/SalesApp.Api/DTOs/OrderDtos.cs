using SalesApp.Api.Models;

namespace SalesApp.Api.DTOs;

// Customers
public record CustomerRequest(string Name, string? Phone, string? Email, string? Address, int? RouteId);
public record CustomerDto(int Id, string Name, string? Phone, string? Email, string? Address, int? RouteId, string? RouteName, DateTime CreatedAt);

// Orders
public record OrderItemRequest(int ItemId, int Quantity, decimal? UnitPrice);
public record OrderRequest(
    int CustomerId, DateTime? OrderDate, DateTime? DeliveryDate, string? Notes,
    List<OrderItemRequest> Items, OrderStatus? Status = null, OrderSource? Source = null);

public record OrderItemDto(
    int Id, int ItemId, string ItemName, int Quantity, decimal UnitPrice, decimal LineTotal,
    ReceivedStatus ReceivedStatus);

public record OrderDto(
    int Id, string OrderNumber, int CustomerId, string CustomerName,
    DateTime OrderDate, DateTime? DeliveryDate,
    OrderStatus Status, PaymentStatus PaymentStatus, OrderSource Source,
    decimal TotalAmount, decimal PaidAmount, decimal RemainingAmount,
    string? Notes, List<OrderItemDto> Items);

public record UpdateOrderStatusRequest(OrderStatus Status);
public record UpdateDeliveryDateRequest(DateTime? DeliveryDate);
public record UpdateReceivedStatusRequest(ReceivedStatus ReceivedStatus);

// Payments
public record PaymentRequest(int OrderId, decimal Amount, DateTime? PaymentDate, string? Method, string? Note);
public record PaymentDto(int Id, int OrderId, decimal Amount, DateTime PaymentDate, string? Method, string? Note);

// Customer search (the "ABC" lookup)
public record CustomerSearchResult(
    CustomerDto Customer,
    decimal TotalOrdered, decimal TotalPaid, decimal TotalRemaining,
    string OverallPaymentStatus,
    int PendingOrders, int DeliveredOrders,
    List<OrderDto> Orders);

// Reports
public record ReportRow(string Label, int OrderCount, decimal TotalAmount, decimal PaidAmount, decimal RemainingAmount);
public record ReportSummary(
    string Period, int TotalOrders, decimal TotalAmount, decimal TotalPaid, decimal TotalRemaining,
    int PendingOrders, int DeliveredOrders, int RowsTotal, List<ReportRow> Rows);
