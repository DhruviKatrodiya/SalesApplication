using SalesApp.Api.Models;

namespace SalesApp.Api.DTOs;

// Pagination
public record PagedResult<T>(IReadOnlyList<T> Items, int Total, int Page, int PageSize);

// Routes (delivery routes)
public record RouteRequest(string Name, string? Description);
public record RouteDto(int Id, string Name, string? Description, int CustomerCount, bool IsActive = true);

// Categories
public record CategoryRequest(string Name, string? Description);
public record CategoryDto(int Id, string Name, string? Description, int SubCategoryCount, bool IsActive = true);

// SubCategories
public record SubCategoryRequest(int CategoryId, string Name, string? Description);
public record SubCategoryDto(int Id, int CategoryId, string CategoryName, string Name, string? Description, int ItemCount, bool IsActive = true);

// Items / Inventory
public record ItemRequest(int SubCategoryId, string Name, string? Sku, string? Unit, int StockQuantity, decimal UnitPrice);
public record ItemDto(
    int Id, int SubCategoryId, string SubCategoryName, string CategoryName,
    string Name, string? Sku, string? Unit, int StockQuantity, int DispatchStock, decimal UnitPrice, bool IsActive = true);

// Inventory batches (purchase-price history)
public record InventoryBatchDto(int Id, int Received, int Remaining, decimal PurchasePrice, DateTime CreatedAt, string? SourceRequestNumber);
public record ItemPriceHistoryDto(
    int ItemId, string Name, int StockQuantity,
    decimal OldestPrice, decimal LatestPrice, decimal AvgCost, decimal StockValue,
    List<InventoryBatchDto> Batches);
// Inventory movement ledger entry (audit)
public record InventoryMovementDto(
    int Id, string Type, int Quantity, decimal UnitCost, decimal TotalCost,
    string RefType, string? Source, bool Reversed, DateTime CreatedAt);

// Stock requests ("My Orders") — a salesperson's request for inventory / low-stock items
public record StockRequestItemRequest(int ItemId, int Quantity, decimal? UnitPrice);
public record StockRequestRequest(string? Notes, List<StockRequestItemRequest> Items);
public record StockRequestItemDto(int ItemId, string ItemName, int Quantity, int CurrentStock, decimal UnitPrice, decimal LineTotal);
public record StockRequestDto(
    int Id, string RequestNumber, DateTime CreatedAt, StockRequestStatus Status,
    decimal TotalAmount, decimal PaidAmount, decimal RemainingAmount, PaymentStatus PaymentStatus,
    string? Notes, List<StockRequestItemDto> Items, bool IsActive = true);

// Stock-request payments
public record StockRequestPaymentRequest(int StockRequestId, decimal Amount, DateTime? PaymentDate, string? Method, string? Note);
public record StockRequestPaymentDto(int Id, int StockRequestId, decimal Amount, DateTime PaymentDate, string? Method, string? Note);
// Salesperson-level advance summary across their stock requests.
// TotalPaid = all money paid. RequestPayments = applied to requests. AdvanceBalance = unused. AdvanceUsed = applied from advance.
public record StockRequestAdvanceDto(decimal TotalPaid, decimal RequestPayments, decimal AdvanceBalance, decimal AdvanceUsed);

// Trucks (managed list, each with its own per-item stock)
public record TruckRequest(string Name);
public record TruckDto(int Id, string Name, DateTime CreatedAt, int ItemCount, int TotalUnits, bool IsActive = true);
// One line of a truck's stock — shaped like an order item picker option.
public record TruckStockItemDto(
    int ItemId, string Name, string? Sku, string? Unit, int Quantity, decimal UnitPrice);

// Dispatch
public record DispatchItemRequest(int ItemId, int Quantity);
// Unsaved dispatch cart, persisted per user. Each line carries the truck it's assigned to.
public record DispatchDraftItemDto(int ItemId, int Quantity, string? TruckLabel);
public record DispatchDraftDto(string? TruckLabel, string? Notes, List<DispatchDraftItemDto> Items);
public record DispatchRequest(string? TruckLabel, string? Notes, List<DispatchItemRequest> Items);
public record DispatchItemDto(int ItemId, string ItemName, int Quantity);
public record DispatchDto(int Id, DateTime DispatchDate, string TruckLabel, string? Notes, List<DispatchItemDto> Items);
