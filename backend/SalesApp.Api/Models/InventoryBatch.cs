namespace SalesApp.Api.Models;

/// <summary>
/// A received lot of an item at a specific purchase price (price-history / batch tracking).
/// Each restock keeps its own price instead of overwriting older stock prices.
/// Phase 1 records receipts (price history + valuation); FIFO consumption is a planned next step.
/// </summary>
public class InventoryBatch
{
    public int Id { get; set; }
    public int UserId { get; set; }   // owning salesperson

    public int ItemId { get; set; }
    public Item? Item { get; set; }

    public int Quantity { get; set; }           // quantity received in this batch
    public decimal PurchasePrice { get; set; }   // the price this batch was received at
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public int? StockRequestId { get; set; }     // source request (null = opening stock)
    public StockRequest? StockRequest { get; set; }
}
