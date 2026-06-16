using System.Security.Claims;
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
public class TrucksController : ControllerBase
{
    private readonly AppDbContext _db;
    public TrucksController(AppDbContext db) => _db = db;

    private int CurrentUserId =>
        int.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier) ?? "0");

    /// <summary>Trucks for the current user (paged), with a quick stock summary.</summary>
    [HttpGet]
    public async Task<ActionResult<PagedResult<TruckDto>>> GetAll(
        [FromQuery] string? search, [FromQuery] bool withStock = false,
        [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        var uid = CurrentUserId;
        var q = _db.Trucks.Include(t => t.Stock).Where(t => t.UserId == uid);
        if (!string.IsNullOrWhiteSpace(search))
        {
            var term = search.Trim();
            q = q.Where(t => t.Name.Contains(term));
        }
        var all = (await q.OrderBy(t => t.Name).ToListAsync())
            .Select(t => new TruckDto(t.Id, t.Name, t.CreatedAt, t.Stock.Count(s => s.Quantity > 0), t.Stock.Sum(s => s.Quantity)))
            // When the picker only wants stocked trucks, hide empty ones.
            .Where(x => !withStock || x.TotalUnits > 0)
            .ToList();

        var total = all.Count;
        var items = all.Skip((page - 1) * pageSize).Take(pageSize).ToList();
        return Ok(new PagedResult<TruckDto>(items, total, page, pageSize));
    }

    /// <summary>A truck's current stock — the items (with quantities) available to order from it.</summary>
    [HttpGet("{id:int}/stock")]
    public async Task<ActionResult<IEnumerable<TruckStockItemDto>>> GetStock(int id)
    {
        if (!await _db.Trucks.AnyAsync(t => t.Id == id && t.UserId == CurrentUserId)) return NotFound();
        var stock = await _db.TruckStocks
            .Include(s => s.Item)
            .Where(s => s.TruckId == id && s.Quantity > 0)
            .OrderBy(s => s.Item!.Name)
            .Select(s => new TruckStockItemDto(
                s.ItemId, s.Item!.Name, s.Item.Sku, s.Item.Unit, s.Quantity, s.Item.UnitPrice))
            .ToListAsync();
        return Ok(stock);
    }

    [HttpPost]
    public async Task<ActionResult<TruckDto>> Create(TruckRequest req)
    {
        var uid = CurrentUserId;
        var name = req.Name?.Trim() ?? string.Empty;
        if (string.IsNullOrWhiteSpace(name))
            return BadRequest(new MessageResponse("Truck name is required."));
        if (await _db.Trucks.AnyAsync(t => t.UserId == uid && t.Name == name))
            return BadRequest(new MessageResponse("A truck with this name already exists."));

        var truck = new Truck { UserId = uid, Name = name };
        _db.Trucks.Add(truck);
        await _db.SaveChangesAsync();
        return Ok(new TruckDto(truck.Id, truck.Name, truck.CreatedAt, 0, 0));
    }

    [HttpPut("{id:int}")]
    public async Task<ActionResult<TruckDto>> Update(int id, TruckRequest req)
    {
        var uid = CurrentUserId;
        var name = req.Name?.Trim() ?? string.Empty;
        if (string.IsNullOrWhiteSpace(name))
            return BadRequest(new MessageResponse("Truck name is required."));
        var truck = await _db.Trucks.Include(t => t.Stock).FirstOrDefaultAsync(t => t.Id == id && t.UserId == uid);
        if (truck is null) return NotFound();
        if (await _db.Trucks.AnyAsync(t => t.UserId == uid && t.Name == name && t.Id != id))
            return BadRequest(new MessageResponse("A truck with this name already exists."));
        truck.Name = name;
        await _db.SaveChangesAsync();
        return Ok(new TruckDto(truck.Id, truck.Name, truck.CreatedAt,
            truck.Stock.Count(s => s.Quantity > 0), truck.Stock.Sum(s => s.Quantity)));
    }

    [HttpDelete("{id:int}")]
    public async Task<IActionResult> Delete(int id)
    {
        var truck = await _db.Trucks.Include(t => t.Stock).FirstOrDefaultAsync(t => t.Id == id && t.UserId == CurrentUserId);
        if (truck is null) return NotFound();
        if (truck.Stock.Any(s => s.Quantity > 0))
            return BadRequest(new MessageResponse("This truck still has stock loaded. Clear it before deleting."));
        _db.Trucks.Remove(truck);
        await _db.SaveChangesAsync();
        return NoContent();
    }
}
