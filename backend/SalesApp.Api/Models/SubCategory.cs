namespace SalesApp.Api.Models;

public class SubCategory
{
    public int Id { get; set; }
    public int UserId { get; set; }   // owning salesperson
    public int CategoryId { get; set; }
    public Category? Category { get; set; }

    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public bool IsActive { get; set; } = true;

    public ICollection<Item> Items { get; set; } = new List<Item>();
}
