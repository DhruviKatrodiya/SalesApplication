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

    private static DispatchDto Map(Dispatch d) => new(
        d.Id, d.DispatchDate, d.TruckLabel, d.Notes,
        d.Items.Select(x => new DispatchItemDto(x.ItemId, x.Item!.Name, x.Quantity)).ToList());

    [HttpGet]
    public async Task<ActionResult<IEnumerable<DispatchDto>>> GetAll()
    {
        var list = await _db.Dispatches
            .Include(d => d.Items).ThenInclude(i => i.Item)
            .OrderByDescending(d => d.DispatchDate)
            .ToListAsync();
        return Ok(list.Select(Map));
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<DispatchDto>> Get(int id)
    {
        var d = await _db.Dispatches.Include(x => x.Items).ThenInclude(i => i.Item)
            .FirstOrDefaultAsync(x => x.Id == id);
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

        var itemIds = req.Items.Select(i => i.ItemId).ToList();
        var items = await _db.Items.Where(i => itemIds.Contains(i.Id)).ToDictionaryAsync(i => i.Id);

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

        var dispatch = new Dispatch
        {
            TruckLabel = string.IsNullOrWhiteSpace(req.TruckLabel) ? "Truck-1" : req.TruckLabel!,
            Notes = req.Notes
        };

        foreach (var line in req.Items)
        {
            var item = items[line.ItemId];
            item.StockQuantity -= line.Quantity;   // decrement godown stock
            dispatch.Items.Add(new DispatchItem { ItemId = line.ItemId, Quantity = line.Quantity });
        }

        _db.Dispatches.Add(dispatch);
        await _db.SaveChangesAsync();

        var saved = await _db.Dispatches.Include(x => x.Items).ThenInclude(i => i.Item)
            .FirstAsync(x => x.Id == dispatch.Id);
        return CreatedAtAction(nameof(Get), new { id = dispatch.Id }, Map(saved));
    }
}
