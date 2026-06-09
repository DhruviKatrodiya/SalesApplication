namespace SalesApp.Api.Models;

public class Payment
{
    public int Id { get; set; }

    public int OrderId { get; set; }
    public Order? Order { get; set; }

    public decimal Amount { get; set; }
    public DateTime PaymentDate { get; set; } = DateTime.UtcNow;
    public string? Method { get; set; }   // Cash, UPI, Bank, etc.
    public string? Note { get; set; }
}
