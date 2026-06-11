using System.Security.Claims;
using System.Text.Json;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SalesApp.Api.Data;
using SalesApp.Api.DTOs;
using SalesApp.Api.Models;

namespace SalesApp.Api.Controllers;

[ApiController]
[Authorize]
[Route("api/[controller]")]
public class DispatchController : ControllerBase
{
    private readonly AppDbContext _db;
    public DispatchController(AppDbContext db) => _db = db;

    private int CurrentUserId =>
        int.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier) ?? "0");

    private static DispatchDto Map(Dispatch d) => new(
        d.Id, d.DispatchDate, d.TruckLabel, d.Notes,
        d.Items.Select(x => new DispatchItemDto(x.ItemId, x.Item!.Name, x.Quantity)).ToList());

    [HttpGet]
    public async Task<ActionResult<PagedResult<DispatchDto>>> GetAll(
        [FromQuery] string? truck, [FromQuery] DateTime? date,
        [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        var uid = CurrentUserId;
        var query = _db.Dispatches
            .Include(d => d.Items).ThenInclude(i => i.Item)
            .Where(d => d.UserId == uid);

        if (!string.IsNullOrWhiteSpace(truck))
        {
            var t = truck.Trim();
            query = query.Where(d => d.TruckLabel.Contains(t));
        }
        if (date is not null)
        {
            var day = date.Value.Date;
            var next = day.AddDays(1);
            query = query.Where(d => d.DispatchDate >= day && d.DispatchDate < next);
        }

        var ordered = query.OrderByDescending(d => d.DispatchDate);
        var total = await ordered.CountAsync();
        var pageItems = await ordered
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .ToListAsync();

        return Ok(new PagedResult<DispatchDto>(pageItems.Select(Map).ToList(), total, page, pageSize));
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<DispatchDto>> Get(int id)
    {
        var d = await _db.Dispatches.Include(x => x.Items).ThenInclude(i => i.Item)
            .FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (d is null) return NotFound();
        return Ok(Map(d));
    }

    /// <summary>
    /// Records a dispatch (truck load) and decrements godown stock for each item.
    /// Validates there is enough stock before committing.
    /// </summary>
    [HttpPost]
    public async Task<ActionResult<DispatchDto>> Create(DispatchRequest req)
    {
        if (req.Items is null || req.Items.Count == 0)
            return BadRequest(new MessageResponse("At least one item is required."));

        var uid = CurrentUserId;
        var itemIds = req.Items.Select(i => i.ItemId).ToList();
        var items = await _db.Items.Where(i => itemIds.Contains(i.Id) && i.UserId == uid).ToDictionaryAsync(i => i.Id);

        // Validate existence and stock availability
        foreach (var line in req.Items)
        {
            if (!items.TryGetValue(line.ItemId, out var item))
                return BadRequest(new MessageResponse($"Item {line.ItemId} not found."));
            if (line.Quantity <= 0)
                return BadRequest(new MessageResponse($"Quantity for '{item.Name}' must be greater than zero."));
            if (item.StockQuantity < line.Quantity)
                return BadRequest(new MessageResponse(
                    $"Insufficient stock for '{item.Name}'. Available: {item.StockQuantity}, requested: {line.Quantity}."));
        }

        var truck = string.IsNullOrWhiteSpace(req.TruckLabel) ? "Truck-1" : req.TruckLabel!.Trim();
        var dayStart = DateTime.UtcNow.Date;
        var dayEnd = dayStart.AddDays(1);

        // Merge into an existing dispatch for the same truck on the same day, if one exists,
        // so a truck has a single consolidated entry per day (same items sum their quantities).
        var dispatch = await _db.Dispatches
            .Include(d => d.Items)
            .FirstOrDefaultAsync(d => d.UserId == uid && d.TruckLabel == truck && d.DispatchDate >= dayStart && d.DispatchDate < dayEnd);

        if (dispatch is null)
        {
            dispatch = new Dispatch { UserId = uid, TruckLabel = truck, Notes = req.Notes };
            _db.Dispatches.Add(dispatch);
        }
        else if (!string.IsNullOrWhiteSpace(req.Notes))
        {
            dispatch.Notes = req.Notes;   // keep the latest note
        }

        foreach (var line in req.Items)
        {
            var item = items[line.ItemId];
            item.StockQuantity -= line.Quantity;    // leaves the godown
            item.DispatchStock += line.Quantity;    // ...and is loaded onto the truck

            // Sum quantity if this item is already on the truck's entry; otherwise add a line.
            var existingLine = dispatch.Items.FirstOrDefault(x => x.ItemId == line.ItemId);
            if (existingLine is not null) existingLine.Quantity += line.Quantity;
            else dispatch.Items.Add(new DispatchItem { ItemId = line.ItemId, Quantity = line.Quantity });
        }

        await _db.SaveChangesAsync();

        var saved = await _db.Dispatches.Include(x => x.Items).ThenInclude(i => i.Item)
            .FirstAsync(x => x.Id == dispatch.Id);
        return CreatedAtAction(nameof(Get), new { id = dispatch.Id }, Map(saved));
    }

    // ---- Draft (unsaved cart) persistence ----

    /// <summary>Returns the current user's saved dispatch draft (empty if none). Each line carries its own truck.</summary>
    [HttpGet("draft")]
    public async Task<ActionResult<DispatchDraftDto>> GetDraft()
    {
        var userId = CurrentUserId;
        var draft = await _db.DispatchDrafts.FirstOrDefaultAsync(d => d.UserId == userId);
        if (draft is null) return Ok(new DispatchDraftDto(null, null, new()));

        var items = JsonSerializer.Deserialize<List<DispatchDraftItemDto>>(draft.ItemsJson) ?? new();
        return Ok(new DispatchDraftDto(draft.TruckLabel, draft.Notes, items));
    }

    /// <summary>Upserts the current user's dispatch draft (whole cart, lines tagged with their truck).</summary>
    [HttpPut("draft")]
    public async Task<ActionResult<DispatchDraftDto>> SaveDraft(DispatchDraftDto req)
    {
        var userId = CurrentUserId;
        var draft = await _db.DispatchDrafts.FirstOrDefaultAsync(d => d.UserId == userId);
        if (draft is null)
        {
            draft = new DispatchDraft { UserId = userId };
            _db.DispatchDrafts.Add(draft);
        }
        draft.TruckLabel = req.TruckLabel;
        draft.Notes = req.Notes;
        draft.ItemsJson = JsonSerializer.Serialize(req.Items ?? new());
        draft.UpdatedAt = DateTime.UtcNow;
        await _db.SaveChangesAsync();
        return Ok(req);
    }

    /// <summary>Clears the current user's dispatch draft (e.g. after recording the dispatch).</summary>
    [HttpDelete("draft")]
    public async Task<IActionResult> ClearDraft()
    {
        var userId = CurrentUserId;
        var draft = await _db.DispatchDrafts.FirstOrDefaultAsync(d => d.UserId == userId);
        if (draft is not null)
        {
            _db.DispatchDrafts.Remove(draft);
            await _db.SaveChangesAsync();
        }
        return NoContent();
    }
}
