namespace SalesApp.Api.Models;

/// <summary>A delivery truck (managed per salesperson) that carries its own stock.</summary>
public class Truck
{
    public int Id { get; set; }
    public int UserId { get; set; }   // owning salesperson
    public string Name { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public bool IsActive { get; set; } = true;

    public ICollection<TruckStock> Stock { get; set; } = new List<TruckStock>();
}

/// <summary>Per-truck stock of a single item (the truck's own inventory).</summary>
public class TruckStock
{
    public int Id { get; set; }

    public int TruckId { get; set; }
    public Truck? Truck { get; set; }

    public int ItemId { get; set; }
    public Item? Item { get; set; }

    public int Quantity { get; set; }
}
