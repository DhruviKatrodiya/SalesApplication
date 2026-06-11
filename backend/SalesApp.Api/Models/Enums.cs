namespace SalesApp.Api.Models;

public enum OrderStatus
{
    Pending = 0,
    Dispatched = 1,
    Delivered = 2,
    Completed = 3,
    Remaining = 4
}

public enum PaymentStatus
{
    Pending = 0,
    Advance = 1,   // partially paid, before dispatch
    Paid = 2,
    Partial = 3    // partially paid, after the order is dispatched/delivered (balance due)
}

public enum ReceivedStatus
{
    Pending = 0,
    Remaining = 1,
    Completed = 2
}

/// <summary>How an order's stock is fulfilled when it is created.</summary>
public enum OrderSource
{
    Inventory = 0,   // deduct from inventory/godown stock only
    Dispatch = 1     // deduct stock AND log a truck dispatch
}

/// <summary>Lifecycle of a salesperson's inventory/stock request ("My Orders").</summary>
public enum StockRequestStatus
{
    Pending = 0,     // submitted, awaiting stock
    Fulfilled = 1,   // stock received -> added to inventory
    Cancelled = 2,
    Done = 3         // fulfilled and closed/completed
}
