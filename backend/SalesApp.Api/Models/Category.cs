namespace SalesApp.Api.Models;

public class Category
{
    public int Id { get; set; }
    public int UserId { get; set; }   // owning salesperson
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public ICollection<SubCategory> SubCategories { get; set; } = new List<SubCategory>();
}
