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
public class ItemsController : OwnedControllerBase
{
    private readonly AppDbContext _db;
    public ItemsController(AppDbContext db) => _db = db;

    private static ItemDto Map(Item i) => new(
        i.Id, i.SubCategoryId, i.SubCategory!.Name, i.SubCategory.Category!.Name,
        i.Name, i.Sku, i.Unit, i.StockQuantity, i.DispatchStock, i.UnitPrice);

    private IQueryable<Item> WithIncludes() =>
        _db.Items.Include(i => i.SubCategory).ThenInclude(s => s!.Category)
            .Where(i => i.UserId == CurrentUserId);

    [HttpGet]
    public async Task<ActionResult<PagedResult<ItemDto>>> GetAll(
        [FromQuery] int? subCategoryId, [FromQuery] bool lowStock = false, [FromQuery] int threshold = 10,
        [FromQuery] string? category = null, [FromQuery] string? item = null, [FromQuery] string? sku = null,
        [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        var query = WithIncludes();
        if (subCategoryId is not null) query = query.Where(i => i.SubCategoryId == subCategoryId);
        if (lowStock) query = query.Where(i => i.StockQuantity <= threshold);
        if (!string.IsNullOrWhiteSpace(category))
        {
            var term = category.Trim();
            query = query.Where(i => i.SubCategory!.Category!.Name.Contains(term) || i.SubCategory.Name.Contains(term));
        }
        if (!string.IsNullOrWhiteSpace(item))
        {
            var term = item.Trim();
            query = query.Where(i => i.Name.Contains(term));
        }
        if (!string.IsNullOrWhiteSpace(sku))
        {
            var term = sku.Trim();
            query = query.Where(i => i.Sku != null && i.Sku.Contains(term));
        }

        var ordered = query.OrderByDescending(i => i.CreatedAt).ThenByDescending(i => i.Id);
        var total = await ordered.CountAsync();
        var pageItems = await ordered
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .ToListAsync();

        return Ok(new PagedResult<ItemDto>(pageItems.Select(Map).ToList(), total, page, pageSize));
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
        var uid = CurrentUserId;
        if (!await _db.SubCategories.AnyAsync(s => s.Id == req.SubCategoryId && s.UserId == uid))
            return BadRequest(new MessageResponse("SubCategory not found."));

        var i = new Item
        {
            UserId = uid,
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
        var uid = CurrentUserId;
        var i = await _db.Items.FirstOrDefaultAsync(x => x.Id == id && x.UserId == uid);
        if (i is null) return NotFound();
        if (!await _db.SubCategories.AnyAsync(s => s.Id == req.SubCategoryId && s.UserId == uid))
            return BadRequest(new MessageResponse("SubCategory not found."));
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
        var i = await _db.Items.FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (i is null) return NotFound();
        _db.Items.Remove(i);
        await _db.SaveChangesAsync();
        return NoContent();
    }
}
