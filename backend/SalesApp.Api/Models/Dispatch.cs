namespace SalesApp.Api.Models;

public class Dispatch
{
    public int Id { get; set; }
    public int UserId { get; set; }   // owning salesperson
    public DateTime DispatchDate { get; set; } = DateTime.UtcNow;
    public string TruckLabel { get; set; } = "Truck-1";  // single truck
    public string? Notes { get; set; }

    public ICollection<DispatchItem> Items { get; set; } = new List<DispatchItem>();
}

public class DispatchItem
{
    public int Id { get; set; }

    public int DispatchId { get; set; }
    public Dispatch? Dispatch { get; set; }

    public int ItemId { get; set; }
    public Item? Item { get; set; }

    public int Quantity { get; set; }
}
