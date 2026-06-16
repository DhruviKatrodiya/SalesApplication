namespace SalesApp.Api.Models;

public class Customer
{
    public int Id { get; set; }
    public int UserId { get; set; }   // owning salesperson
    public string Name { get; set; } = string.Empty;
    public string? Phone { get; set; }
    public string? Email { get; set; }
    public string? Address { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public bool IsActive { get; set; } = true;

    public int? RouteId { get; set; }
    public DeliveryRoute? Route { get; set; }

    public ICollection<Order> Orders { get; set; } = new List<Order>();
}
