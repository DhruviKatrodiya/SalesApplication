namespace SalesApp.Api.DTOs;

// Pagination
public record PagedResult<T>(IReadOnlyList<T> Items, int Total, int Page, int PageSize);

// Routes (delivery routes)
public record RouteRequest(string Name, string? Description);
public record RouteDto(int Id, string Name, string? Description, int CustomerCount);

// Categories
public record CategoryRequest(string Name, string? Description);
public record CategoryDto(int Id, string Name, string? Description, int SubCategoryCount);

// SubCategories
public record SubCategoryRequest(int CategoryId, string Name, string? Description);
public record SubCategoryDto(int Id, int CategoryId, string CategoryName, string Name, string? Description, int ItemCount);

// Items / Inventory
public record ItemRequest(int SubCategoryId, string Name, string? Sku, string? Unit, int StockQuantity, decimal UnitPrice);
public record ItemDto(
    int Id, int SubCategoryId, string SubCategoryName, string CategoryName,
    string Name, string? Sku, string? Unit, int StockQuantity, int DispatchStock, decimal UnitPrice);

// Dispatch
public record DispatchItemRequest(int ItemId, int Quantity);
// Unsaved dispatch cart, persisted per user. Each line carries the truck it's assigned to.
public record DispatchDraftItemDto(int ItemId, int Quantity, string? TruckLabel);
public record DispatchDraftDto(string? TruckLabel, string? Notes, List<DispatchDraftItemDto> Items);
public record DispatchRequest(string? TruckLabel, string? Notes, List<DispatchItemRequest> Items);
public record DispatchItemDto(int ItemId, string ItemName, int Quantity);
public record DispatchDto(int Id, DateTime DispatchDate, string TruckLabel, string? Notes, List<DispatchItemDto> Items);
