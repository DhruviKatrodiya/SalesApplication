using SalesApp.Api.DTOs;
using SalesApp.Api.Models;

namespace SalesApp.Api.Services;

public static class Mappers
{
    public static OrderDto ToDto(Order o) => new(
        o.Id, o.OrderNumber, o.CustomerId, o.Customer?.Name ?? string.Empty,
        o.OrderDate, o.DeliveryDate, o.Status, o.PaymentStatus, o.Source,
        o.TotalAmount, o.PaidAmount, o.RemainingAmount, o.Notes,
        o.Items.Select(i => new OrderItemDto(
            i.Id, i.ItemId, i.Item?.Name ?? string.Empty,
            i.Quantity, i.UnitPrice, i.LineTotal, i.ReceivedStatus)).ToList());

    public static CustomerDto ToDto(Customer c) =>
        new(c.Id, c.Name, c.Phone, c.Email, c.Address, c.RouteId, c.Route?.Name, c.CreatedAt);
}
