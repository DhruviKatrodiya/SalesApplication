using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SalesApp.Api.Data;
using SalesApp.Api.DTOs;
using SalesApp.Api.Models;
using SalesApp.Api.Services;

namespace SalesApp.Api.Controllers;

[ApiController]
[Authorize]
[Route("api/[controller]")]
public class PaymentsController : ControllerBase
{
    private readonly AppDbContext _db;
    public PaymentsController(AppDbContext db) => _db = db;

    private static PaymentDto Map(Payment p) =>
        new(p.Id, p.OrderId, p.Amount, p.PaymentDate, p.Method, p.Note);

    [HttpGet("by-order/{orderId:int}")]
    public async Task<ActionResult<IEnumerable<PaymentDto>>> GetByOrder(int orderId)
    {
        var list = await _db.Payments
            .Where(p => p.OrderId == orderId)
            .OrderByDescending(p => p.PaymentDate)
            .ToListAsync();
        return Ok(list.Select(Map));
    }

    /// <summary>Records a payment/settlement and recomputes the order's paid/remaining/status.</summary>
    [HttpPost]
    public async Task<ActionResult<OrderDto>> Create(PaymentRequest req)
    {
        var order = await _db.Orders
            .Include(o => o.Customer)
            .Include(o => o.Items).ThenInclude(i => i.Item)
            .Include(o => o.Payments)
            .FirstOrDefaultAsync(o => o.Id == req.OrderId);
        if (order is null) return BadRequest(new MessageResponse("Order not found."));
        if (req.Amount <= 0) return BadRequest(new MessageResponse("Amount must be greater than zero."));

        var payment = new Payment
        {
            OrderId = req.OrderId,
            Amount = req.Amount,
            PaymentDate = req.PaymentDate ?? DateTime.UtcNow,
            Method = req.Method,
            Note = req.Note
        };
        order.Payments.Add(payment);
        OrderMath.Recalculate(order);

        await _db.SaveChangesAsync();
        return Ok(Mappers.ToDto(order));
    }

    /// <summary>Mark an order fully settled (records a payment for the remaining balance).</summary>
    [HttpPost("settle/{orderId:int}")]
    public async Task<ActionResult<OrderDto>> Settle(int orderId)
    {
        var order = await _db.Orders
            .Include(o => o.Customer)
            .Include(o => o.Items).ThenInclude(i => i.Item)
            .Include(o => o.Payments)
            .FirstOrDefaultAsync(o => o.Id == orderId);
        if (order is null) return NotFound();

        var remaining = order.TotalAmount - order.Payments.Sum(p => p.Amount);
        if (remaining > 0)
        {
            order.Payments.Add(new Payment
            {
                OrderId = orderId,
                Amount = remaining,
                PaymentDate = DateTime.UtcNow,
                Method = "Settlement",
                Note = "Full settlement"
            });
        }
        OrderMath.Recalculate(order);
        await _db.SaveChangesAsync();
        return Ok(Mappers.ToDto(order));
    }

    [HttpDelete("{id:int}")]
    public async Task<ActionResult<OrderDto>> Delete(int id)
    {
        var payment = await _db.Payments.FindAsync(id);
        if (payment is null) return NotFound();

        var orderId = payment.OrderId;
        _db.Payments.Remove(payment);
        await _db.SaveChangesAsync();

        var order = await _db.Orders
            .Include(o => o.Customer)
            .Include(o => o.Items).ThenInclude(i => i.Item)
            .Include(o => o.Payments)
            .FirstAsync(o => o.Id == orderId);
        OrderMath.Recalculate(order);
        await _db.SaveChangesAsync();
        return Ok(Mappers.ToDto(order));
    }
}
