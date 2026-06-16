namespace SalesApp.Api.Models;

/// <summary>A delivery route a customer can be assigned to.
/// Named DeliveryRoute to avoid clashing with Microsoft.AspNetCore.Routing.Route;
/// the DbSet/table and the API are still "Routes".</summary>
public class DeliveryRoute
{
    public int Id { get; set; }
    public int UserId { get; set; }   // owning salesperson
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public bool IsActive { get; set; } = true;

    public ICollection<Customer> Customers { get; set; } = new List<Customer>();
}
