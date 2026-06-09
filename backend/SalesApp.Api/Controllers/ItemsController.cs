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
public class ItemsController : ControllerBase
{
    private readonly AppDbContext _db;
    public ItemsController(AppDbContext db) => _db = db;

    private static ItemDto Map(Item i) => new(
        i.Id, i.SubCategoryId, i.SubCategory!.Name, i.SubCategory.Category!.Name,
        i.Name, i.Sku, i.Unit, i.StockQuantity, i.UnitPrice);

    private IQueryable<Item> WithIncludes() =>
        _db.Items.Include(i => i.SubCategory).ThenInclude(s => s!.Category);

    [HttpGet]
    public async Task<ActionResult<IEnumerable<ItemDto>>> GetAll(
        [FromQuery] int? subCategoryId, [FromQuery] bool lowStock = false, [FromQuery] int threshold = 10)
    {
        var query = WithIncludes();
        if (subCategoryId is not null) query = query.Where(i => i.SubCategoryId == subCategoryId);
        if (lowStock) query = query.Where(i => i.StockQuantity <= threshold);

        var list = await query.OrderBy(i => i.Name).ToListAsync();
        return Ok(list.Select(Map));
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<ItemDto>> Get(int id)
    {
        var i = await WithIncludes().FirstOrDefaultAsync(x => x.Id == id);
        if (i is null) return NotFound();
        return Ok(Map(i));
    }

    [HttpPost]
    public async Task<ActionResult<ItemDto>> Create(ItemRequest req)
    {
        if (!await _db.SubCategories.AnyAsync(s => s.Id == req.SubCategoryId))
            return BadRequest(new MessageResponse("SubCategory not found."));

        var i = new Item
        {
            SubCategoryId = req.SubCategoryId,
            Name = req.Name,
            Sku = req.Sku,
            Unit = req.Unit,
            StockQuantity = req.StockQuantity,
            UnitPrice = req.UnitPrice
        };
        _db.Items.Add(i);
        await _db.SaveChangesAsync();
        i = await WithIncludes().FirstAsync(x => x.Id == i.Id);
        return CreatedAtAction(nameof(Get), new { id = i.Id }, Map(i));
    }

    [HttpPut("{id:int}")]
    public async Task<ActionResult<ItemDto>> Update(int id, ItemRequest req)
    {
        var i = await _db.Items.FindAsync(id);
        if (i is null) return NotFound();
        i.SubCategoryId = req.SubCategoryId;
        i.Name = req.Name;
        i.Sku = req.Sku;
        i.Unit = req.Unit;
        i.StockQuantity = req.StockQuantity;
        i.UnitPrice = req.UnitPrice;
        await _db.SaveChangesAsync();
        i = await WithIncludes().FirstAsync(x => x.Id == i.Id);
        return Ok(Map(i));
    }

    [HttpDelete("{id:int}")]
    public async Task<IActionResult> Delete(int id)
    {
        var i = await _db.Items.FindAsync(id);
        if (i is null) return NotFound();
        _db.Items.Remove(i);
        await _db.SaveChangesAsync();
        return NoContent();
    }
}
