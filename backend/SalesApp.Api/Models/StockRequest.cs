namespace SalesApp.Api.Models;

/// <summary>
/// A salesperson's request for inventory / low-stock items ("My Orders").
/// Distinct from customer Orders: no customer, no delivery. It carries a cost
/// (item price x qty) and its own payment ledger so the salesperson can pay
/// for the stock they take.
/// </summary>
public class StockRequest
{
    public int Id { get; set; }
    public string RequestNumber { get; set; } = string.Empty;

    public int? SalesmanId { get; set; }   // the requesting salesperson (owner)
    public AppUser? Salesman { get; set; }

    public StockRequestStatus Status { get; set; } = StockRequestStatus.Pending;
    public string? Notes { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public decimal TotalAmount { get; set; }
    public decimal PaidAmount { get; set; }
    public decimal RemainingAmount { get; set; }
    public PaymentStatus PaymentStatus { get; set; } = PaymentStatus.Pending;

    public ICollection<StockRequestItem> Items { get; set; } = new List<StockRequestItem>();
    public ICollection<StockRequestPayment> Payments { get; set; } = new List<StockRequestPayment>();
}

public class StockRequestItem
{
    public int Id { get; set; }
    public int StockRequestId { get; set; }
    public StockRequest? StockRequest { get; set; }

    public int ItemId { get; set; }
    public Item? Item { get; set; }

    public int Quantity { get; set; }
    public decimal UnitPrice { get; set; }
    public decimal LineTotal { get; set; }
}

public class StockRequestPayment
{
    public int Id { get; set; }
    public int StockRequestId { get; set; }
    public StockRequest? StockRequest { get; set; }

    public decimal Amount { get; set; }
    public DateTime PaymentDate { get; set; } = DateTime.UtcNow;
    public string? Method { get; set; }
    public string? Note { get; set; }
}
