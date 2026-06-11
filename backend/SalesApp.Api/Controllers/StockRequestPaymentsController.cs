using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SalesApp.Api.Data;
using SalesApp.Api.DTOs;
using SalesApp.Api.Models;

namespace SalesApp.Api.Controllers;

/// <summary>Payments a salesperson records against their own inventory requests ("My Orders").</summary>
[ApiController]
[Authorize]
[Route("api/stock-request-payments")]
public class StockRequestPaymentsController : ControllerBase
{
    private readonly AppDbContext _db;
    public StockRequestPaymentsController(AppDbContext db) => _db = db;

    private int CurrentUserId =>
        int.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier) ?? "0");

    private ObjectResult NotOwner() =>
        StatusCode(403, new MessageResponse("You can only manage payments for your own requests."));

    private static StockRequestPaymentDto Map(StockRequestPayment p) =>
        new(p.Id, p.StockRequestId, p.Amount, p.PaymentDate, p.Method, p.Note);

    private IQueryable<StockRequest> WithIncludes() =>
        _db.StockRequests.Include(r => r.Items).ThenInclude(i => i.Item).Include(r => r.Payments);

    [HttpGet("by-request/{requestId:int}")]
    public async Task<ActionResult<IEnumerable<StockRequestPaymentDto>>> GetByRequest(int requestId)
    {
        if (!await _db.StockRequests.AnyAsync(r => r.Id == requestId && r.SalesmanId == CurrentUserId)) return NotOwner();
        var list = await _db.StockRequestPayments
            .Where(p => p.StockRequestId == requestId)
            .OrderByDescending(p => p.PaymentDate)
            .ToListAsync();
        return Ok(list.Select(Map));
    }

    [HttpPost]
    public async Task<ActionResult<StockRequestDto>> Create(StockRequestPaymentRequest req)
    {
        var request = await WithIncludes().FirstOrDefaultAsync(r => r.Id == req.StockRequestId);
        if (request is null) return BadRequest(new MessageResponse("Request not found."));
        if (request.SalesmanId != CurrentUserId) return NotOwner();
        if (req.Amount <= 0) return BadRequest(new MessageResponse("Amount must be greater than zero."));

        request.Payments.Add(new StockRequestPayment
        {
            StockRequestId = req.StockRequestId,
            Amount = req.Amount,
            PaymentDate = req.PaymentDate ?? DateTime.UtcNow,
            Method = req.Method,
            Note = req.Note
        });
        StockRequestsController.RecalcPayment(request);
        await _db.SaveChangesAsync();
        return Ok(MapRequest(request));
    }

    [HttpPost("settle/{requestId:int}")]
    public async Task<ActionResult<StockRequestDto>> Settle(int requestId)
    {
        var request = await WithIncludes().FirstOrDefaultAsync(r => r.Id == requestId);
        if (request is null) return NotFound();
        if (request.SalesmanId != CurrentUserId) return NotOwner();

        var remaining = request.TotalAmount - request.Payments.Sum(p => p.Amount);
        if (remaining > 0)
        {
            request.Payments.Add(new StockRequestPayment
            {
                StockRequestId = requestId,
                Amount = remaining,
                PaymentDate = DateTime.UtcNow,
                Method = "Settlement",
                Note = "Full settlement"
            });
        }
        StockRequestsController.RecalcPayment(request);
        await _db.SaveChangesAsync();
        return Ok(MapRequest(request));
    }

    [HttpDelete("{id:int}")]
    public async Task<ActionResult<StockRequestDto>> Delete(int id)
    {
        var payment = await _db.StockRequestPayments.FindAsync(id);
        if (payment is null) return NotFound();
        if (!await _db.StockRequests.AnyAsync(r => r.Id == payment.StockRequestId && r.SalesmanId == CurrentUserId)) return NotOwner();

        var requestId = payment.StockRequestId;
        _db.StockRequestPayments.Remove(payment);
        await _db.SaveChangesAsync();

        var request = await WithIncludes().FirstAsync(r => r.Id == requestId);
        StockRequestsController.RecalcPayment(request);
        await _db.SaveChangesAsync();
        return Ok(MapRequest(request));
    }

    private static StockRequestDto MapRequest(StockRequest r) => new(
        r.Id, r.RequestNumber, r.CreatedAt, r.Status,
        r.TotalAmount, r.PaidAmount, r.RemainingAmount, r.PaymentStatus, r.Notes,
        r.Items.Select(i => new StockRequestItemDto(
            i.ItemId, i.Item?.Name ?? string.Empty, i.Quantity, i.Item?.StockQuantity ?? 0,
            i.UnitPrice, i.LineTotal)).ToList());
}
