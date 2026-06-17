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
    private readonly Services.InventoryService _inv;
    public DispatchController(AppDbContext db, Services.InventoryService inv) { _db = db; _inv = inv; }

    private int CurrentUserId =>
        int.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier) ?? "0");

    private static DispatchDto Map(Dispatch d) => new(
        d.Id, d.DispatchDate, d.TruckLabel, d.Notes,
        d.Items.Select(x => new DispatchItemDto(x.ItemId, x.Item!.Name, x.Quantity)).ToList(), d.IsActive);

    [HttpGet]
    public async Task<ActionResult<PagedResult<DispatchDto>>> GetAll(
        [FromQuery] string? truck, [FromQuery] DateTime? date, [FromQuery] string? active,
        [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        var uid = CurrentUserId;
        var query = _db.Dispatches
            .Include(d => d.Items).ThenInclude(i => i.Item)
            .Where(d => d.UserId == uid);

        if (active != "all") query = query.Where(d => d.IsActive == (active != "inactive"));
        if (!string.IsNullOrWhiteSpace(truck))
        {
            var t = truck.Trim();
            query = query.Where(d => d.TruckLabel == t);   // exact match (selected from the truck filter dropdown)
        }
        if (date is not null)
        {
            var day = date.Value.Date;
            var next = day.AddDays(1);
            query = query.Where(d => d.DispatchDate >= day && d.DispatchDate < next);
        }

        var ordered = query.OrderByDescending(d => d.DispatchDate).ThenByDescending(d => d.Id);
        var total = await ordered.CountAsync();
        var pageItems = await ordered
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .ToListAsync();

        return Ok(new PagedResult<DispatchDto>(pageItems.Select(Map).ToList(), total, page, pageSize));
    }

    /// <summary>Distinct truck labels that appear in this user's dispatch history (for the history filter).</summary>
    [HttpGet("trucks")]
    public async Task<ActionResult<IEnumerable<string>>> TruckLabels()
    {
        var uid = CurrentUserId;
        var labels = await _db.Dispatches
            .Where(d => d.UserId == uid)
            .Select(d => d.TruckLabel)
            .Distinct()
            .OrderBy(x => x)
            .ToListAsync();
        return Ok(labels);
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

        // Resolve (or auto-register) the managed truck so its per-truck stock can be loaded.
        var truckEntity = await _db.Trucks.Include(t => t.Stock)
            .FirstOrDefaultAsync(t => t.UserId == uid && t.Name == truck);
        if (truckEntity is null)
        {
            truckEntity = new Truck { UserId = uid, Name = truck };
            _db.Trucks.Add(truckEntity);
        }

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

        // Persist the truck + dispatch shell first so the dispatch has an Id; the FIFO
        // consumption movements are tagged with it, which lets edit/delete reverse exactly.
        await _db.SaveChangesAsync();

        foreach (var line in req.Items)
        {
            var item = items[line.ItemId];
            // FIFO-consume the godown stock (oldest batch first) and log the movement + COGS.
            await _inv.ConsumeFifoAsync(item, line.Quantity, "Dispatch", dispatch.Id, truck);
            item.DispatchStock += line.Quantity;    // ...and is loaded onto the truck (aggregate, all trucks)

            // Add to this specific truck's own stock ledger.
            var ts = truckEntity.Stock.FirstOrDefault(s => s.ItemId == line.ItemId);
            if (ts is null) { ts = new TruckStock { ItemId = line.ItemId, Quantity = 0 }; truckEntity.Stock.Add(ts); }
            ts.Quantity += line.Quantity;

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

    /// <summary>
    /// Reverses a dispatch's stock effects: returns the loaded quantities to godown stock,
    /// and removes them from the aggregate dispatch stock and the specific truck's ledger.
    /// </summary>
    private async Task ReverseDispatchStockAsync(Dispatch dispatch)
    {
        var uid = dispatch.UserId;
        // Precise restore (batch quantities + godown stock) when movements are linked to this dispatch.
        var linked = await _db.InventoryTransactions.AnyAsync(t =>
            t.RefType == "Dispatch" && t.RefId == dispatch.Id && t.Type == InventoryTxnType.Out && !t.Reversed);
        if (linked) await _inv.ReverseAsync("Dispatch", dispatch.Id);

        var itemIds = dispatch.Items.Select(i => i.ItemId).ToList();
        var items = await _db.Items.Where(i => itemIds.Contains(i.Id)).ToDictionaryAsync(i => i.Id);
        var truckEntity = await _db.Trucks.Include(t => t.Stock)
            .FirstOrDefaultAsync(t => t.UserId == uid && t.Name == dispatch.TruckLabel);

        foreach (var line in dispatch.Items)
        {
            if (items.TryGetValue(line.ItemId, out var it))
            {
                if (!linked) it.StockQuantity += line.Quantity;   // legacy dispatches had no movement link
                it.DispatchStock = Math.Max(0, it.DispatchStock - line.Quantity);
            }
            var ts = truckEntity?.Stock.FirstOrDefault(s => s.ItemId == line.ItemId);
            if (ts is not null) ts.Quantity = Math.Max(0, ts.Quantity - line.Quantity);
        }
        await _db.SaveChangesAsync();
    }

    /// <summary>Edits a dispatch: restores the old stock effects then re-applies the new lines.</summary>
    [HttpPut("{id:int}")]
    public async Task<ActionResult<DispatchDto>> Update(int id, DispatchRequest req)
    {
        var uid = CurrentUserId;
        var dispatch = await _db.Dispatches.Include(d => d.Items)
            .FirstOrDefaultAsync(d => d.Id == id && d.UserId == uid);
        if (dispatch is null) return NotFound();
        if (!dispatch.IsActive)
            return BadRequest(new MessageResponse("This dispatch is inactive. Activate it before editing."));
        if (req.Items is null || req.Items.Count == 0)
            return BadRequest(new MessageResponse("At least one item is required."));

        var allIds = req.Items.Select(i => i.ItemId).Concat(dispatch.Items.Select(i => i.ItemId)).Distinct().ToList();
        var items = await _db.Items.Where(i => allIds.Contains(i.Id) && i.UserId == uid).ToDictionaryAsync(i => i.Id);

        // Quantity this dispatch currently holds per item — it returns to godown when reversed.
        var heldByThis = dispatch.Items.GroupBy(i => i.ItemId).ToDictionary(g => g.Key, g => g.Sum(x => x.Quantity));

        // Validate up front: reversal persists immediately, so we must not fail half-way.
        foreach (var line in req.Items)
        {
            if (!items.TryGetValue(line.ItemId, out var item))
                return BadRequest(new MessageResponse($"Item {line.ItemId} not found."));
            if (line.Quantity <= 0)
                return BadRequest(new MessageResponse($"Quantity for '{item.Name}' must be greater than zero."));
            var requested = req.Items.Where(x => x.ItemId == line.ItemId).Sum(x => x.Quantity);
            var available = item.StockQuantity + (heldByThis.TryGetValue(line.ItemId, out var h) ? h : 0);
            if (requested > available)
                return BadRequest(new MessageResponse(
                    $"Insufficient stock for '{item.Name}'. Available: {available}, requested: {requested}."));
        }

        // Reverse the existing stock effects, then replace the lines.
        await ReverseDispatchStockAsync(dispatch);

        var truck = string.IsNullOrWhiteSpace(req.TruckLabel) ? dispatch.TruckLabel : req.TruckLabel!.Trim();
        var truckEntity = await _db.Trucks.Include(t => t.Stock).FirstOrDefaultAsync(t => t.UserId == uid && t.Name == truck);
        if (truckEntity is null) { truckEntity = new Truck { UserId = uid, Name = truck }; _db.Trucks.Add(truckEntity); }

        _db.DispatchItems.RemoveRange(dispatch.Items);
        dispatch.Items.Clear();
        dispatch.TruckLabel = truck;
        dispatch.Notes = req.Notes;
        await _db.SaveChangesAsync();

        foreach (var line in req.Items)
        {
            var item = items[line.ItemId];
            await _inv.ConsumeFifoAsync(item, line.Quantity, "Dispatch", dispatch.Id, truck);
            item.DispatchStock += line.Quantity;
            var ts = truckEntity.Stock.FirstOrDefault(s => s.ItemId == line.ItemId);
            if (ts is null) { ts = new TruckStock { ItemId = line.ItemId, Quantity = 0 }; truckEntity.Stock.Add(ts); }
            ts.Quantity += line.Quantity;
            var existing = dispatch.Items.FirstOrDefault(x => x.ItemId == line.ItemId);
            if (existing is not null) existing.Quantity += line.Quantity;
            else dispatch.Items.Add(new DispatchItem { ItemId = line.ItemId, Quantity = line.Quantity });
        }
        await _db.SaveChangesAsync();

        var saved = await _db.Dispatches.Include(x => x.Items).ThenInclude(i => i.Item).FirstAsync(x => x.Id == dispatch.Id);
        return Ok(Map(saved));
    }

    /// <summary>
    /// Soft-deletes a dispatch: flags it inactive and returns its loaded stock to godown
    /// (off the truck). The record is kept and can be reactivated.
    /// </summary>
    [HttpDelete("{id:int}")]
    public async Task<IActionResult> Delete(int id)
    {
        var uid = CurrentUserId;
        var dispatch = await _db.Dispatches.Include(d => d.Items)
            .FirstOrDefaultAsync(d => d.Id == id && d.UserId == uid);
        if (dispatch is null) return NotFound();
        if (!dispatch.IsActive) return NoContent();   // already inactive

        await ReverseDispatchStockAsync(dispatch);
        dispatch.IsActive = false;
        await _db.SaveChangesAsync();
        return NoContent();
    }

    /// <summary>Reactivates a soft-deleted dispatch: re-loads its stock onto the truck (FIFO).</summary>
    [HttpPut("{id:int}/activate")]
    public async Task<ActionResult<DispatchDto>> Activate(int id)
    {
        var uid = CurrentUserId;
        var dispatch = await _db.Dispatches.Include(d => d.Items)
            .FirstOrDefaultAsync(d => d.Id == id && d.UserId == uid);
        if (dispatch is null) return NotFound();
        if (dispatch.IsActive)
        {
            var current = await _db.Dispatches.Include(x => x.Items).ThenInclude(i => i.Item).FirstAsync(x => x.Id == id);
            return Ok(Map(current));
        }

        var itemIds = dispatch.Items.Select(i => i.ItemId).ToList();
        var items = await _db.Items.Where(i => itemIds.Contains(i.Id) && i.UserId == uid).ToDictionaryAsync(i => i.Id);

        // Make sure there is enough godown stock to re-load before changing anything.
        foreach (var line in dispatch.Items)
        {
            if (!items.TryGetValue(line.ItemId, out var item))
                return BadRequest(new MessageResponse($"Item {line.ItemId} no longer exists; cannot reactivate this dispatch."));
            if (item.StockQuantity < line.Quantity)
                return BadRequest(new MessageResponse(
                    $"Insufficient stock for '{item.Name}' to reactivate. Available: {item.StockQuantity}, needed: {line.Quantity}."));
        }

        var truckEntity = await _db.Trucks.Include(t => t.Stock).FirstOrDefaultAsync(t => t.UserId == uid && t.Name == dispatch.TruckLabel);
        if (truckEntity is null) { truckEntity = new Truck { UserId = uid, Name = dispatch.TruckLabel }; _db.Trucks.Add(truckEntity); await _db.SaveChangesAsync(); }

        foreach (var line in dispatch.Items)
        {
            var item = items[line.ItemId];
            await _inv.ConsumeFifoAsync(item, line.Quantity, "Dispatch", dispatch.Id, dispatch.TruckLabel);
            item.DispatchStock += line.Quantity;
            var ts = truckEntity.Stock.FirstOrDefault(s => s.ItemId == line.ItemId);
            if (ts is null) { ts = new TruckStock { ItemId = line.ItemId, Quantity = 0 }; truckEntity.Stock.Add(ts); }
            ts.Quantity += line.Quantity;
        }
        dispatch.IsActive = true;
        await _db.SaveChangesAsync();

        var saved = await _db.Dispatches.Include(x => x.Items).ThenInclude(i => i.Item).FirstAsync(x => x.Id == dispatch.Id);
        return Ok(Map(saved));
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
