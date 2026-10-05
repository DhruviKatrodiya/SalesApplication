namespace SalesApp.Api.Models;

/// <summary>
/// One entry in an order's tracking timeline: a status change, or (when From == To) a note such as a
/// delivery-date change.
/// </summary>
public class OrderStatusHistory
{
    public int Id { get; set; }

    public int OrderId { get; set; }
    public Order? Order { get; set; }

    public OrderStatus? FromStatus { get; set; }   // null for the first entry (order created)
    public OrderStatus ToStatus { get; set; }
    public string? Note { get; set; }

    public DateTime ChangedAt { get; set; } = DateTime.UtcNow;
    public string? ChangedByName { get; set; }
}
