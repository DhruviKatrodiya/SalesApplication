using SalesApp.Api.Models;

namespace SalesApp.Api.Services;

public static class OrderMath
{
    /// <summary>
    /// Recomputes PaidAmount/RemainingAmount/PaymentStatus on an order from its payments.
    /// The order's Payments collection must be loaded.
    /// </summary>
    public static void Recalculate(Order order)
    {
        order.PaidAmount = order.Payments.Sum(p => p.Amount);
        order.RemainingAmount = order.TotalAmount - order.PaidAmount;

        if (order.PaidAmount <= 0)
            order.PaymentStatus = PaymentStatus.Pending;
        else if (order.PaidAmount >= order.TotalAmount)
            order.PaymentStatus = PaymentStatus.Paid;
        else
            order.PaymentStatus = PaymentStatus.Advance;
    }
}
