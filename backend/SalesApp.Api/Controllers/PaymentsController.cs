using System.Security.Claims;
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

    private int CurrentUserId =>
        int.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier) ?? "0");

    private ObjectResult NotOwner() =>
        StatusCode(403, new MessageResponse("You can only manage payments for your own orders."));

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
        if (order.SalesmanId != CurrentUserId) return NotOwner();
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

    /// <summary>
    /// Applies the customer's available advance balance to an order: moves the overpayment out of the
    /// source order(s) and records it as a payment on this order. Money is conserved (total paid is unchanged).
    /// </summary>
    [HttpPost("apply-advance/{orderId:int}")]
    public async Task<ActionResult<OrderDto>> ApplyAdvance(int orderId, ApplyAdvanceRequest? req)
    {
        var target0 = await _db.Orders.FirstOrDefaultAsync(o => o.Id == orderId);
        if (target0 is null) return BadRequest(new MessageResponse("Order not found."));
        if (target0.SalesmanId != CurrentUserId) return NotOwner();

        // All of this customer's orders (the advance lives as overpayment on some of them).
        var customerOrders = await _db.Orders
            .Include(o => o.Customer)
            .Include(o => o.Items).ThenInclude(i => i.Item)
            .Include(o => o.Payments)
            .Where(o => o.CustomerId == target0.CustomerId && o.IsActive)
            .ToListAsync();
        var target = customerOrders.First(o => o.Id == orderId);

        var available = OrderMath.AdvanceBalance(customerOrders);
        var targetRemaining = Math.Max(0, target.TotalAmount - target.Payments.Sum(p => p.Amount));
        var toApply = Math.Min(available, targetRemaining);
        if (req?.Amount is decimal a && a > 0) toApply = Math.Min(toApply, a);
        if (toApply <= 0) return BadRequest(new MessageResponse("No advance balance available to apply."));

        // Pull the amount out of the source orders that hold the overpayment.
        var left = toApply;
        foreach (var src in customerOrders.Where(o => o.Id != target.Id && OrderMath.OverpaidOn(o) > 0)
                                          .OrderBy(o => o.OrderDate))
        {
            if (left <= 0) break;
            var take = Math.Min(left, OrderMath.OverpaidOn(src));
            src.Payments.Add(new Payment
            {
                OrderId = src.Id,
                Amount = -take,
                PaymentDate = DateTime.UtcNow,
                Method = PaymentMethods.AdvanceTransfer,
                Note = $"Advance applied to {target.OrderNumber}"
            });
            OrderMath.Recalculate(src);
            left -= take;
        }

        target.Payments.Add(new Payment
        {
            OrderId = target.Id,
            Amount = toApply,
            PaymentDate = DateTime.UtcNow,
            Method = PaymentMethods.Advance,
            Note = "Applied from advance balance"
        });
        OrderMath.Recalculate(target);

        await _db.SaveChangesAsync();
        return Ok(Mappers.ToDto(target));
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
        if (order.SalesmanId != CurrentUserId) return NotOwner();

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
        if (!await _db.Orders.AnyAsync(o => o.Id == payment.OrderId && o.SalesmanId == CurrentUserId)) return NotOwner();
        if (payment.Method is PaymentMethods.Advance or PaymentMethods.AdvanceTransfer)
            return BadRequest(new MessageResponse("Advance entries can't be deleted directly."));

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
