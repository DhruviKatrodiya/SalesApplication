namespace SalesApp.Api.Models;

public class Item
{
    public int Id { get; set; }
    public int SubCategoryId { get; set; }
    public SubCategory? SubCategory { get; set; }

    public string Name { get; set; } = string.Empty;
    public string? Sku { get; set; }
    public string? Unit { get; set; }          // e.g. pcs, kg, box
    public int StockQuantity { get; set; }      // current godown / inventory stock
    public int DispatchStock { get; set; }       // stock loaded onto the truck (assigned for dispatch)
    public decimal UnitPrice { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
