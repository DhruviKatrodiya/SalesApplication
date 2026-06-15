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
        // Never a negative due: any overpayment is tracked as the customer's advance balance,
        // not as a negative remaining on the order.
        order.RemainingAmount = Math.Max(0, order.TotalAmount - order.PaidAmount);

        if (order.PaidAmount <= 0)
            order.PaymentStatus = PaymentStatus.Pending;
        else if (order.PaidAmount >= order.TotalAmount)
            order.PaymentStatus = PaymentStatus.Paid;
        else if (order.Status == OrderStatus.Pending)
            order.PaymentStatus = PaymentStatus.Advance;   // partial, paid before dispatch
        else
            order.PaymentStatus = PaymentStatus.Partial;   // partial, but already dispatched/delivered
    }

    /// <summary>Amount paid against a single order, never more than the order total.</summary>
    public static decimal AppliedToOrder(Order order) => Math.Min(order.PaidAmount, order.TotalAmount);

    /// <summary>Overpayment on a single order — the part that becomes advance.</summary>
    public static decimal OverpaidOn(Order order) => Math.Max(0, order.PaidAmount - order.TotalAmount);

    /// <summary>Customer advance = money paid beyond what each order required, summed across orders.</summary>
    public static decimal AdvanceBalance(IEnumerable<Order> orders) => orders.Sum(OverpaidOn);
}

/// <summary>Reserved payment methods used by the advance-balance machinery.</summary>
public static class PaymentMethods
{
    public const string Advance = "Advance";                 // advance applied TO an order (positive)
    public const string AdvanceTransfer = "AdvanceTransfer"; // overpayment moved OUT of an order (negative)
}
