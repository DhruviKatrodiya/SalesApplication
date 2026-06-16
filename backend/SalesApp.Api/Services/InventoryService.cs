using Microsoft.EntityFrameworkCore;
using SalesApp.Api.Data;
using SalesApp.Api.Models;

namespace SalesApp.Api.Services;

/// <summary>
/// Batch/lot inventory with FIFO consumption and a movement ledger.
/// Methods persist their own changes so subsequent reads see fresh batch state.
/// </summary>
public class InventoryService
{
    private readonly AppDbContext _db;
    public InventoryService(AppDbContext db) => _db = db;

    /// <summary>Backfill an opening batch for stock not yet represented by batches (legacy/imported stock).</summary>
    private async Task EnsureOpeningBatchAsync(Item item)
    {
        var batched = await _db.InventoryBatches.Where(b => b.ItemId == item.Id).SumAsync(b => (int?)b.Quantity) ?? 0;
        if (batched >= item.StockQuantity) return;
        var diff = item.StockQuantity - batched;
        var ob = new InventoryBatch
        {
            UserId = item.UserId, ItemId = item.Id, InitialQuantity = diff, Quantity = diff,
            PurchasePrice = item.UnitPrice, CreatedAt = item.CreatedAt
        };
        _db.InventoryBatches.Add(ob);
        await _db.SaveChangesAsync();
        _db.InventoryTransactions.Add(new InventoryTransaction
        {
            UserId = item.UserId, ItemId = item.Id, BatchId = ob.Id, Type = InventoryTxnType.In,
            Quantity = diff, UnitCost = item.UnitPrice, TotalCost = diff * item.UnitPrice,
            RefType = "Opening", Source = "Opening stock", CreatedAt = item.CreatedAt
        });
        await _db.SaveChangesAsync();
    }

    /// <summary>Receive stock as a new price-tagged batch (does not merge with existing batches).</summary>
    public async Task ReceiveAsync(Item item, int qty, decimal price, string refType, int? refId, string? source)
    {
        if (qty <= 0) return;
        await EnsureOpeningBatchAsync(item);
        var batch = new InventoryBatch
        {
            UserId = item.UserId, ItemId = item.Id, InitialQuantity = qty, Quantity = qty,
            PurchasePrice = price, StockRequestId = refType == "Fulfill" ? refId : null
        };
        _db.InventoryBatches.Add(batch);
        item.StockQuantity += qty;
        await _db.SaveChangesAsync();
        _db.InventoryTransactions.Add(new InventoryTransaction
        {
            UserId = item.UserId, ItemId = item.Id, BatchId = batch.Id, Type = InventoryTxnType.In,
            Quantity = qty, UnitCost = price, TotalCost = qty * price, RefType = refType, RefId = refId, Source = source
        });
        await _db.SaveChangesAsync();
    }

    /// <summary>Consume qty FIFO (oldest batch first). Returns COGS. Throws if insufficient.</summary>
    public async Task<decimal> ConsumeFifoAsync(Item item, int qty, string refType, int? refId, string? source)
    {
        if (qty <= 0) return 0;
        await EnsureOpeningBatchAsync(item);
        var batches = await _db.InventoryBatches.Where(b => b.ItemId == item.Id && b.Quantity > 0)
            .OrderBy(b => b.CreatedAt).ThenBy(b => b.Id).ToListAsync();
        var available = batches.Sum(b => b.Quantity);
        if (available < qty)
            throw new InvalidOperationException($"Insufficient inventory stock for '{item.Name}'. Available: {available}, requested: {qty}.");

        var remaining = qty;
        decimal cogs = 0;
        foreach (var b in batches)
        {
            if (remaining <= 0) break;
            var take = Math.Min(remaining, b.Quantity);
            b.Quantity -= take;
            remaining -= take;
            cogs += take * b.PurchasePrice;
            _db.InventoryTransactions.Add(new InventoryTransaction
            {
                UserId = item.UserId, ItemId = item.Id, BatchId = b.Id, Type = InventoryTxnType.Out,
                Quantity = take, UnitCost = b.PurchasePrice, TotalCost = take * b.PurchasePrice,
                RefType = refType, RefId = refId, Source = source
            });
        }
        item.StockQuantity -= qty;
        await _db.SaveChangesAsync();
        return cogs;
    }

    /// <summary>Reverse all (non-reversed) OUT movements for a document — restores batch quantities and stock.</summary>
    public async Task ReverseAsync(string refType, int refId)
    {
        var txns = await _db.InventoryTransactions.Include(t => t.Batch)
            .Where(t => t.RefType == refType && t.RefId == refId && t.Type == InventoryTxnType.Out && !t.Reversed)
            .ToListAsync();
        if (txns.Count == 0) return;
        var itemIds = txns.Select(t => t.ItemId).Distinct().ToList();
        var items = await _db.Items.Where(i => itemIds.Contains(i.Id)).ToDictionaryAsync(i => i.Id);
        foreach (var t in txns)
        {
            if (t.Batch != null) t.Batch.Quantity += t.Quantity;
            if (items.TryGetValue(t.ItemId, out var it)) it.StockQuantity += t.Quantity;
            t.Reversed = true;
        }
        await _db.SaveChangesAsync();
    }
}
