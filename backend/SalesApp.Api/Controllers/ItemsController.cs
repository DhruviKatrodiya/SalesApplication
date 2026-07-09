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
        i.Name, i.Sku, i.Unit, i.StockQuantity, i.DispatchStock, i.UnitPrice, i.IsActive);

    private IQueryable<Item> WithIncludes() =>
        _db.Items.Include(i => i.SubCategory).ThenInclude(s => s!.Category)
            .Where(i => i.UserId == CurrentUserId);

    [HttpGet]
    public async Task<ActionResult<PagedResult<ItemDto>>> GetAll(
        [FromQuery] int? subCategoryId, [FromQuery] bool lowStock = false, [FromQuery] int threshold = 10,
        [FromQuery] string? category = null, [FromQuery] string? item = null, [FromQuery] string? sku = null,
        [FromQuery] string? search = null,
        [FromQuery] string? active = null, [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        var query = WithIncludes();
        if (active != "all") query = query.Where(i => i.IsActive == (active != "inactive"));
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
        if (!string.IsNullOrWhiteSpace(search))
        {
            var term = search.Trim();
            query = query.Where(i =>
                i.Name.Contains(term) ||
                (i.Sku != null && i.Sku.Contains(term)) ||
                i.SubCategory!.Category!.Name.Contains(term) ||
                i.SubCategory.Name.Contains(term));
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
        // Opening-stock batch so price history & valuation are complete from the start.
        if (i.StockQuantity > 0)
        {
            var ob = new InventoryBatch
            {
                UserId = uid, ItemId = i.Id, InitialQuantity = i.StockQuantity, Quantity = i.StockQuantity, PurchasePrice = i.UnitPrice
            };
            _db.InventoryBatches.Add(ob);
            await _db.SaveChangesAsync();
            _db.InventoryTransactions.Add(new InventoryTransaction
            {
                UserId = uid, ItemId = i.Id, BatchId = ob.Id, Type = InventoryTxnType.In,
                Quantity = i.StockQuantity, UnitCost = i.UnitPrice, TotalCost = i.StockQuantity * i.UnitPrice,
                RefType = "Opening", Source = "Opening stock"
            });
            await _db.SaveChangesAsync();
        }
        i = await WithIncludes().FirstAsync(x => x.Id == i.Id);
        return CreatedAtAction(nameof(Get), new { id = i.Id }, Map(i));
    }

    /// <summary>Purchase-price history (batches) for an item, with current valuation. Batches are paged.</summary>
    [HttpGet("{id:int}/price-history")]
    public async Task<ActionResult<ItemPriceHistoryDto>> PriceHistory(
        int id, [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        var item = await _db.Items.FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (item is null) return NotFound();

        // All batches are needed for an exact FIFO valuation; only the returned list is paged.
        var batches = await _db.InventoryBatches
            .Where(b => b.ItemId == id)
            .OrderByDescending(b => b.CreatedAt).ThenByDescending(b => b.Id)
            .Select(b => new InventoryBatchDto(b.Id, b.InitialQuantity, b.Quantity, b.PurchasePrice, b.CreatedAt,
                b.StockRequest != null ? b.StockRequest.RequestNumber : null))
            .ToListAsync();

        // Exact FIFO valuation from the remaining quantity in each batch.
        var remainingQty = batches.Sum(b => b.Remaining);
        var batchValue = batches.Sum(b => b.Remaining * b.PurchasePrice);
        // Any stock not yet represented by a batch (untouched legacy stock) is valued at the item price.
        var unbatched = Math.Max(0, item.StockQuantity - remainingQty);
        var stockValue = decimal.Round(batchValue + unbatched * item.UnitPrice, 2);
        var avgCost = item.StockQuantity > 0 ? decimal.Round(stockValue / item.StockQuantity, 2) : item.UnitPrice;
        var oldest = batches.Count > 0 ? batches[^1].PurchasePrice : item.UnitPrice;
        var latest = batches.Count > 0 ? batches[0].PurchasePrice : item.UnitPrice;

        var pagedBatches = batches.Skip((page - 1) * pageSize).Take(pageSize).ToList();

        return Ok(new ItemPriceHistoryDto(item.Id, item.Name, item.StockQuantity,
            oldest, latest, avgCost, stockValue, pagedBatches, batches.Count));
    }

    /// <summary>Inventory movement ledger for an item (receipts and FIFO consumption) — audit trail (paged).</summary>
    [HttpGet("{id:int}/movements")]
    public async Task<ActionResult<PagedResult<InventoryMovementDto>>> Movements(
        int id, [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        if (!await _db.Items.AnyAsync(x => x.Id == id && x.UserId == CurrentUserId)) return NotFound();
        var query = _db.InventoryTransactions
            .Where(t => t.ItemId == id)
            .OrderByDescending(t => t.CreatedAt).ThenByDescending(t => t.Id);
        var total = await query.CountAsync();
        var list = await query
            .Skip((page - 1) * pageSize).Take(pageSize)
            .Select(t => new InventoryMovementDto(t.Id, t.Type == InventoryTxnType.In ? "IN" : "OUT",
                t.Quantity, t.UnitCost, t.TotalCost, t.RefType, t.Source, t.Reversed, t.CreatedAt))
            .ToListAsync();
        return Ok(new PagedResult<InventoryMovementDto>(list, total, page, pageSize));
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

        // Don't deactivate an item that still exists in another table / on another page
        // (loaded on a truck, or referenced by an order, stock request or dispatch).
        var usedInOrders = await _db.OrderItems.AnyAsync(oi => oi.ItemId == id);
        var usedInRequests = await _db.StockRequestItems.AnyAsync(ri => ri.ItemId == id);
        var usedOnTruck = await _db.TruckStocks.AnyAsync(s => s.ItemId == id && s.Quantity > 0);
        var usedInDispatch = await _db.DispatchItems.AnyAsync(di => di.ItemId == id);
        if (usedInOrders || usedInRequests || usedOnTruck || usedInDispatch)
        {
            var places = new List<string>();
            if (usedInOrders) places.Add("customer orders");
            if (usedInRequests) places.Add("stock requests");
            if (usedOnTruck) places.Add("truck stock");
            if (usedInDispatch) places.Add("dispatches");
            return BadRequest(new MessageResponse(
                $"This item is still used in {string.Join(", ", places)} and cannot be deactivated. Remove it from there first."));
        }

        i.IsActive = false;   // soft delete; inventory batches & movements are preserved
        await _db.SaveChangesAsync();
        return NoContent();
    }

    [HttpPut("{id:int}/activate")]
    public async Task<IActionResult> Activate(int id)
    {
        var i = await _db.Items.FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (i is null) return NotFound();
        i.IsActive = true;
        await _db.SaveChangesAsync();
        return NoContent();
    }
}
