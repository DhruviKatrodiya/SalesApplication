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
    Advance = 1,   // partially paid
    Paid = 2
}

public enum ReceivedStatus
{
    Pending = 0,
    Remaining = 1,
    Completed = 2
}
