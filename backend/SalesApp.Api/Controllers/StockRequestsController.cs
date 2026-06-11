using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SalesApp.Api.Data;
using SalesApp.Api.DTOs;
using SalesApp.Api.Models;

namespace SalesApp.Api.Controllers;

/// <summary>
/// "My Orders" — a salesperson's inventory/stock requests. Each salesperson sees and
/// manages only their own requests. Fulfilling a request adds the quantities to inventory.
/// </summary>
[ApiController]
[Authorize]
[Route("api/stock-requests")]
public class StockRequestsController : ControllerBase
{
    private readonly AppDbContext _db;
    public StockRequestsController(AppDbContext db) => _db = db;

    private int CurrentUserId =>
        int.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier) ?? "0");

    private ObjectResult NotOwner() =>
        StatusCode(403, new MessageResponse("You can only manage your own requests."));

    private IQueryable<StockRequest> WithIncludes() =>
        _db.StockRequests.Include(r => r.Items).ThenInclude(i => i.Item).Include(r => r.Payments);

    private static StockRequestDto Map(StockRequest r) => new(
        r.Id, r.RequestNumber, r.CreatedAt, r.Status,
        r.TotalAmount, r.PaidAmount, r.RemainingAmount, r.PaymentStatus, r.Notes,
        r.Items.Select(i => new StockRequestItemDto(
            i.ItemId, i.Item?.Name ?? string.Empty, i.Quantity, i.Item?.StockQuantity ?? 0,
            i.UnitPrice, i.LineTotal)).ToList());

    /// <summary>Recomputes the cost of a request's line items from item prices.</summary>
    private async Task BuildLinesAsync(StockRequest request, List<StockRequestItemRequest> lines)
    {
        var ids = lines.Select(l => l.ItemId).ToList();
        var items = await _db.Items.Where(i => ids.Contains(i.Id)).ToDictionaryAsync(i => i.Id);
        request.Items.Clear();
        foreach (var line in lines)
        {
            // Use the entered amount if provided, otherwise fall back to the item's price.
            var price = line.UnitPrice ?? (items.TryGetValue(line.ItemId, out var it) ? it.UnitPrice : 0m);
            request.Items.Add(new StockRequestItem
            {
                ItemId = line.ItemId,
                Quantity = line.Quantity,
                UnitPrice = price,
                LineTotal = price * line.Quantity
            });
        }
        request.TotalAmount = request.Items.Sum(i => i.LineTotal);
    }

    /// <summary>
    /// Recomputes paid/remaining/payment-status from the request's payments.
    /// Partial payment shows as Advance before the stock is received, and Partial once
    /// the request is fulfilled/done.
    /// </summary>
    internal static void RecalcPayment(StockRequest r)
    {
        r.PaidAmount = r.Payments.Sum(p => p.Amount);
        r.RemainingAmount = r.TotalAmount - r.PaidAmount;
        if (r.PaidAmount <= 0) r.PaymentStatus = PaymentStatus.Pending;
        else if (r.PaidAmount >= r.TotalAmount && r.TotalAmount > 0) r.PaymentStatus = PaymentStatus.Paid;
        else r.PaymentStatus = r.Status == StockRequestStatus.Pending ? PaymentStatus.Advance : PaymentStatus.Partial;
    }

    [HttpGet]
    public async Task<ActionResult<PagedResult<StockRequestDto>>> GetAll(
        [FromQuery] StockRequestStatus? status, [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        var uid = CurrentUserId;
        var query = WithIncludes().Where(r => r.SalesmanId == uid);
        if (status is not null) query = query.Where(r => r.Status == status);

        var ordered = query.OrderByDescending(r => r.CreatedAt).ThenByDescending(r => r.Id);
        var total = await ordered.CountAsync();
        var items = (await ordered.Skip((page - 1) * pageSize).Take(pageSize).ToListAsync())
            .Select(Map).ToList();

        return Ok(new PagedResult<StockRequestDto>(items, total, page, pageSize));
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<StockRequestDto>> Get(int id)
    {
        var r = await WithIncludes().FirstOrDefaultAsync(x => x.Id == id);
        if (r is null) return NotFound();
        if (r.SalesmanId != CurrentUserId) return NotOwner();
        return Ok(Map(r));
    }

    [HttpPost]
    public async Task<ActionResult<StockRequestDto>> Create(StockRequestRequest req)
    {
        if (req.Items is null || req.Items.Count == 0)
            return BadRequest(new MessageResponse("At least one item is required."));
        if (req.Items.Any(i => i.Quantity <= 0))
            return BadRequest(new MessageResponse("Quantities must be greater than zero."));

        var request = new StockRequest
        {
            RequestNumber = await GenerateNumberAsync(),
            SalesmanId = CurrentUserId,
            Status = StockRequestStatus.Pending,
            Notes = req.Notes
        };
        await BuildLinesAsync(request, req.Items);
        RecalcPayment(request);   // no payments yet -> Pending

        _db.StockRequests.Add(request);
        await _db.SaveChangesAsync();
        var saved = await WithIncludes().FirstAsync(r => r.Id == request.Id);
        return CreatedAtAction(nameof(Get), new { id = request.Id }, Map(saved));
    }

    [HttpPut("{id:int}")]
    public async Task<ActionResult<StockRequestDto>> Update(int id, StockRequestRequest req)
    {
        var request = await _db.StockRequests.Include(r => r.Items).Include(r => r.Payments).FirstOrDefaultAsync(r => r.Id == id);
        if (request is null) return NotFound();
        if (request.SalesmanId != CurrentUserId) return NotOwner();
        if (request.Status != StockRequestStatus.Pending)
            return BadRequest(new MessageResponse("Only pending requests can be edited."));
        if (req.Items is null || req.Items.Count == 0)
            return BadRequest(new MessageResponse("At least one item is required."));

        request.Notes = req.Notes;
        _db.StockRequestItems.RemoveRange(request.Items);
        await BuildLinesAsync(request, req.Items);
        RecalcPayment(request);   // total changed -> refresh remaining/status against existing payments

        await _db.SaveChangesAsync();
        var saved = await WithIncludes().FirstAsync(r => r.Id == id);
        return Ok(Map(saved));
    }

    /// <summary>Mark a request fulfilled — the requested quantities are added to inventory stock.</summary>
    [HttpPut("{id:int}/fulfill")]
    public async Task<ActionResult<StockRequestDto>> Fulfill(int id)
    {
        var request = await _db.StockRequests.Include(r => r.Items).Include(r => r.Payments).FirstOrDefaultAsync(r => r.Id == id);
        if (request is null) return NotFound();
        if (request.SalesmanId != CurrentUserId) return NotOwner();
        if (request.Status != StockRequestStatus.Pending)
            return BadRequest(new MessageResponse("Only pending requests can be fulfilled."));

        var itemIds = request.Items.Select(i => i.ItemId).ToList();
        var items = await _db.Items.Where(i => itemIds.Contains(i.Id)).ToDictionaryAsync(i => i.Id);
        foreach (var line in request.Items)
            if (items.TryGetValue(line.ItemId, out var item))
                item.StockQuantity += line.Quantity;   // requested stock received -> restock inventory

        request.Status = StockRequestStatus.Fulfilled;
        RecalcPayment(request);   // partial payment now reads as "Partial"
        await _db.SaveChangesAsync();
        var saved = await WithIncludes().FirstAsync(r => r.Id == id);
        return Ok(Map(saved));
    }

    /// <summary>Mark a fulfilled request done/closed.</summary>
    [HttpPut("{id:int}/done")]
    public async Task<ActionResult<StockRequestDto>> Done(int id)
    {
        var request = await _db.StockRequests.Include(r => r.Items).Include(r => r.Payments).FirstOrDefaultAsync(r => r.Id == id);
        if (request is null) return NotFound();
        if (request.SalesmanId != CurrentUserId) return NotOwner();
        if (request.Status != StockRequestStatus.Fulfilled)
            return BadRequest(new MessageResponse("Only fulfilled requests can be marked done."));

        request.Status = StockRequestStatus.Done;
        RecalcPayment(request);
        await _db.SaveChangesAsync();
        var saved = await WithIncludes().FirstAsync(r => r.Id == id);
        return Ok(Map(saved));
    }

    [HttpPut("{id:int}/cancel")]
    public async Task<ActionResult<StockRequestDto>> Cancel(int id)
    {
        var request = await _db.StockRequests.Include(r => r.Items).Include(r => r.Payments).FirstOrDefaultAsync(r => r.Id == id);
        if (request is null) return NotFound();
        if (request.SalesmanId != CurrentUserId) return NotOwner();
        if (request.Status == StockRequestStatus.Cancelled || request.Status == StockRequestStatus.Done)
            return BadRequest(new MessageResponse("This request can no longer be cancelled."));

        // If it was already fulfilled, return the stock it added back out of inventory.
        if (request.Status == StockRequestStatus.Fulfilled)
        {
            var itemIds = request.Items.Select(i => i.ItemId).ToList();
            var items = await _db.Items.Where(i => itemIds.Contains(i.Id)).ToDictionaryAsync(i => i.Id);
            foreach (var line in request.Items)
                if (items.TryGetValue(line.ItemId, out var item))
                    item.StockQuantity -= line.Quantity;
        }

        request.Status = StockRequestStatus.Cancelled;
        RecalcPayment(request);
        await _db.SaveChangesAsync();
        var saved = await WithIncludes().FirstAsync(r => r.Id == id);
        return Ok(Map(saved));
    }

    [HttpDelete("{id:int}")]
    public async Task<IActionResult> Delete(int id)
    {
        var request = await _db.StockRequests.FindAsync(id);
        if (request is null) return NotFound();
        if (request.SalesmanId != CurrentUserId) return NotOwner();
        _db.StockRequests.Remove(request);
        await _db.SaveChangesAsync();
        return NoContent();
    }

    private async Task<string> GenerateNumberAsync()
    {
        var year = DateTime.UtcNow.Year;
        var count = await _db.StockRequests.CountAsync(r => r.CreatedAt.Year == year);
        return $"REQ-{year}-{(count + 1):D4}";
    }
}
