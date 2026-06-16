using SalesApp.Api.DTOs;
using SalesApp.Api.Models;

namespace SalesApp.Api.Services;

public static class Mappers
{
    public static OrderDto ToDto(Order o) => new(
        o.Id, o.OrderNumber, o.CustomerId, o.Customer?.Name ?? string.Empty,
        o.OrderDate, o.DeliveryDate, o.Status, o.PaymentStatus, o.Source,
        // Remaining is derived so it is never negative even for orders persisted before
        // overpayment was tracked as advance — the overpaid part shows as the customer's advance.
        o.TotalAmount, o.PaidAmount, Math.Max(0, o.TotalAmount - o.PaidAmount), o.Notes,
        o.Items.Select(i => new OrderItemDto(
            i.Id, i.ItemId, i.Item?.Name ?? string.Empty,
            i.Quantity, i.UnitPrice, i.LineTotal, i.ReceivedStatus)).ToList(),
        o.TruckId, o.Truck?.Name, o.IsActive);

    public static CustomerDto ToDto(Customer c) =>
        new(c.Id, c.Name, c.Phone, c.Email, c.Address, c.RouteId, c.Route?.Name, c.CreatedAt, c.IsActive);
}
