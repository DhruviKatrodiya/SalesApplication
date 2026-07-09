using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SalesApp.Api.Data;
using SalesApp.Api.DTOs;
using SalesApp.Api.Models;
using SalesApp.Api.Services;

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
    private readonly InventoryService _inv;
    public StockRequestsController(AppDbContext db, InventoryService inv) { _db = db; _inv = inv; }

    private int CurrentUserId =>
        int.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier) ?? "0");

    private ObjectResult NotOwner() =>
        StatusCode(403, new MessageResponse("You can only manage your own requests."));

    private IQueryable<StockRequest> WithIncludes() =>
        _db.StockRequests.Include(r => r.Items).ThenInclude(i => i.Item).Include(r => r.Payments);

    private static StockRequestDto Map(StockRequest r) => new(
        r.Id, r.RequestNumber, r.CreatedAt, r.Status,
        // Remaining derived so it's never negative — overpayment shows as advance balance.
        r.TotalAmount, r.PaidAmount, Math.Max(0, r.TotalAmount - r.PaidAmount), r.PaymentStatus, r.Notes,
        r.Items.Select(i => new StockRequestItemDto(
            i.ItemId, i.Item?.Name ?? string.Empty, i.Quantity, i.Item?.StockQuantity ?? 0,
            i.UnitPrice, i.LineTotal)).ToList(), r.IsActive);

    /// <summary>Recomputes the cost of a request's line items from item prices.</summary>
    private async Task BuildLinesAsync(StockRequest request, List<StockRequestItemRequest> lines)
    {
        var ids = lines.Select(l => l.ItemId).ToList();
        var uid = CurrentUserId;
        var items = await _db.Items.Where(i => ids.Contains(i.Id) && i.UserId == uid).ToDictionaryAsync(i => i.Id);
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
        // Never a negative due: overpayment is tracked as the salesperson's advance balance.
        r.RemainingAmount = Math.Max(0, r.TotalAmount - r.PaidAmount);
        if (r.PaidAmount <= 0) r.PaymentStatus = PaymentStatus.Pending;
        else if (r.PaidAmount >= r.TotalAmount && r.TotalAmount > 0) r.PaymentStatus = PaymentStatus.Paid;
        else r.PaymentStatus = r.Status == StockRequestStatus.Pending ? PaymentStatus.Advance : PaymentStatus.Partial;
    }

    /// <summary>Overpayment on a single request — the part that becomes advance.</summary>
    internal static decimal OverpaidOn(StockRequest r) => Math.Max(0, r.PaidAmount - r.TotalAmount);
    /// <summary>Salesperson advance = money paid beyond what each request required.</summary>
    internal static decimal AdvanceBalance(IEnumerable<StockRequest> requests) => requests.Sum(OverpaidOn);

    [HttpGet]
    public async Task<ActionResult<PagedResult<StockRequestDto>>> GetAll(
        [FromQuery] string? requestNumber, [FromQuery] DateTime? date, [FromQuery] string? item,
        [FromQuery] string? search,
        [FromQuery] StockRequestStatus? status, [FromQuery] PaymentStatus? paymentStatus, [FromQuery] string? active,
        [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;
        if (status is not null && !Enum.IsDefined(status.Value))
            return BadRequest(new MessageResponse("Invalid status filter."));
        if (paymentStatus is not null && !Enum.IsDefined(paymentStatus.Value))
            return BadRequest(new MessageResponse("Invalid payment status filter."));

        var uid = CurrentUserId;
        var query = WithIncludes().Where(r => r.SalesmanId == uid);
        if (active != "all") query = query.Where(r => r.IsActive == (active != "inactive"));
        if (!string.IsNullOrWhiteSpace(requestNumber))
        {
            var t = requestNumber.Trim();
            query = query.Where(r => r.RequestNumber.Contains(t));
        }
        if (date is not null)
        {
            var day = date.Value.Date;
            var next = day.AddDays(1);
            query = query.Where(r => r.CreatedAt >= day && r.CreatedAt < next);
        }
        if (!string.IsNullOrWhiteSpace(item))
        {
            var t = item.Trim();
            query = query.Where(r => r.Items.Any(i => i.Item!.Name.Contains(t)));
        }
        if (!string.IsNullOrWhiteSpace(search))
        {
            var t = search.Trim();
            query = query.Where(r => r.RequestNumber.Contains(t) || r.Items.Any(i => i.Item!.Name.Contains(t)));
        }
        if (status is not null) query = query.Where(r => r.Status == status);
        if (paymentStatus is not null) query = query.Where(r => r.PaymentStatus == paymentStatus);

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

        // Validate the generated number's prefix/date/sequence format before persisting.
        if (!DocNumber.IsValid(request.RequestNumber, "REQ"))
            return StatusCode(500, new MessageResponse("Failed to generate a valid request number."));

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
        {
            if (!items.TryGetValue(line.ItemId, out var item)) continue;
            // Received stock becomes its own price-tagged FIFO batch (does not merge with older batches).
            await _inv.ReceiveAsync(item, line.Quantity, line.UnitPrice, "Fulfill", request.Id, request.RequestNumber);
            item.UnitPrice = line.UnitPrice;       // display the latest price (history preserved in batches)
        }

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

        // If it was already fulfilled, remove the batches this request created and take only their
        // still-remaining quantity back out of stock (any already-consumed units stay accounted for).
        if (request.Status == StockRequestStatus.Fulfilled)
        {
            var itemIds = request.Items.Select(i => i.ItemId).ToList();
            var items = await _db.Items.Where(i => itemIds.Contains(i.Id)).ToDictionaryAsync(i => i.Id);
            var batches = await _db.InventoryBatches.Where(b => b.StockRequestId == id).ToListAsync();
            foreach (var batch in batches)
            {
                if (items.TryGetValue(batch.ItemId, out var item))
                    item.StockQuantity -= batch.Quantity;   // remaining only
                batch.Quantity = 0;   // deplete (keep batch for audit; movements reference it)
            }
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
        request.IsActive = false;
        await _db.SaveChangesAsync();
        return NoContent();
    }

    [HttpPut("{id:int}/activate")]
    public async Task<IActionResult> Activate(int id)
    {
        var request = await _db.StockRequests.FindAsync(id);
        if (request is null) return NotFound();
        if (request.SalesmanId != CurrentUserId) return NotOwner();
        request.IsActive = true;
        await _db.SaveChangesAsync();
        return NoContent();
    }

    private async Task<string> GenerateNumberAsync()
    {
        var now = DateTime.UtcNow;
        // Monthly sequence: count this month's requests so the number resets to 000001 each month.
        var seq = await _db.StockRequests.CountAsync(r => r.CreatedAt.Year == now.Year && r.CreatedAt.Month == now.Month) + 1;
        var number = DocNumber.Format("REQ", now, seq);
        while (await _db.StockRequests.AnyAsync(r => r.RequestNumber == number))
            number = DocNumber.Format("REQ", now, ++seq);
        return number;
    }
}
