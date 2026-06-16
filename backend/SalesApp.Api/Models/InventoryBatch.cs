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

    public int InitialQuantity { get; set; }     // quantity received in this batch
    public int Quantity { get; set; }            // quantity remaining (FIFO consumed)
    public decimal PurchasePrice { get; set; }   // the price this batch was received at
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public int? StockRequestId { get; set; }     // source request (null = opening stock)
    public StockRequest? StockRequest { get; set; }
}

/// <summary>An audit record of a single stock movement (receipt or FIFO consumption).</summary>
public class InventoryTransaction
{
    public int Id { get; set; }
    public int UserId { get; set; }

    public int ItemId { get; set; }
    public Item? Item { get; set; }

    public int? BatchId { get; set; }            // the batch this movement touched
    public InventoryBatch? Batch { get; set; }

    public InventoryTxnType Type { get; set; }
    public int Quantity { get; set; }            // units moved (always positive)
    public decimal UnitCost { get; set; }        // per-unit cost of the affected batch
    public decimal TotalCost { get; set; }       // Quantity * UnitCost

    public string RefType { get; set; } = string.Empty;  // e.g. "Fulfill", "Dispatch", "Order", "Opening"
    public int? RefId { get; set; }                       // id of the source document
    public string? Source { get; set; }                  // human-readable label (REQ-…, ORD-…, Truck-…)
    public bool Reversed { get; set; }                   // true once an Out movement has been reversed
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
