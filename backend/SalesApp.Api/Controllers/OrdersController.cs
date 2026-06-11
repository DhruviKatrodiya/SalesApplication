using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SalesApp.Api.Data;
using SalesApp.Api.DTOs;
using SalesApp.Api.Models;
using SalesApp.Api.Services;

namespace SalesApp.Api.Controllers;

[ApiController]
[Authorize]
[Route("api/[controller]")]
public class OrdersController : ControllerBase
{
    private readonly AppDbContext _db;
    public OrdersController(AppDbContext db) => _db = db;

    private int CurrentUserId =>
        int.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier) ?? "0");

    private ObjectResult NotOwner() =>
        StatusCode(403, new MessageResponse("You can only manage your own orders."));

    private IQueryable<Order> WithIncludes() =>
        _db.Orders
            .Include(o => o.Customer)
            .Include(o => o.Items).ThenInclude(i => i.Item)
            .Include(o => o.Payments);

    [HttpGet]
    public async Task<ActionResult<PagedResult<OrderDto>>> GetAll(
        [FromQuery] string? customer, [FromQuery] DateTime? orderDate,
        [FromQuery] OrderStatus? status, [FromQuery] PaymentStatus? paymentStatus,
        [FromQuery] int? customerId, [FromQuery] bool mine = false,
        [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        var query = WithIncludes();
        if (mine)
        {
            var uid = CurrentUserId;
            query = query.Where(o => o.SalesmanId == uid);   // only the salesperson's own orders
        }
        if (customerId is not null) query = query.Where(o => o.CustomerId == customerId);
        if (!string.IsNullOrWhiteSpace(customer))
        {
            var t = customer.Trim();
            query = query.Where(o => o.Customer!.Name.Contains(t));
        }
        if (orderDate is not null)
        {
            var d = orderDate.Value.Date;
            var next = d.AddDays(1);
            query = query.Where(o => o.OrderDate >= d && o.OrderDate < next);
        }
        if (status is not null) query = query.Where(o => o.Status == status);
        if (paymentStatus is not null) query = query.Where(o => o.PaymentStatus == paymentStatus);

        var ordered = query.OrderByDescending(o => o.OrderDate);
        var total = await ordered.CountAsync();
        var items = (await ordered.Skip((page - 1) * pageSize).Take(pageSize).ToListAsync())
            .Select(Mappers.ToDto).ToList();

        return Ok(new PagedResult<OrderDto>(items, total, page, pageSize));
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<OrderDto>> Get(int id)
    {
        var o = await WithIncludes().FirstOrDefaultAsync(x => x.Id == id);
        if (o is null) return NotFound();
        return Ok(Mappers.ToDto(o));
    }

    [HttpPost]
    public async Task<ActionResult<OrderDto>> Create(OrderRequest req)
    {
        if (!await _db.Customers.AnyAsync(c => c.Id == req.CustomerId))
            return BadRequest(new MessageResponse("Customer not found."));
        if (req.Items is null || req.Items.Count == 0)
            return BadRequest(new MessageResponse("At least one order item is required."));

        var itemIds = req.Items.Select(i => i.ItemId).ToList();
        var items = await _db.Items.Where(i => itemIds.Contains(i.Id)).ToDictionaryAsync(i => i.Id);

        var source = req.Source ?? OrderSource.Inventory;

        var order = new Order
        {
            OrderNumber = await GenerateOrderNumberAsync(),
            CustomerId = req.CustomerId,
            SalesmanId = CurrentUserId,   // owned by the salesperson who creates it
            OrderDate = req.OrderDate ?? DateTime.UtcNow,
            DeliveryDate = req.DeliveryDate,
            Notes = req.Notes,
            Status = OrderStatus.Pending,
            Source = source
        };

        // Validate existence, quantity, and available stock for every line — against the
        // correct bucket: Inventory orders draw from godown stock, Dispatch orders draw
        // from the truck's loaded (dispatch) stock.
        foreach (var line in req.Items)
        {
            if (!items.TryGetValue(line.ItemId, out var item))
                return BadRequest(new MessageResponse($"Item {line.ItemId} not found."));
            if (line.Quantity <= 0)
                return BadRequest(new MessageResponse($"Quantity for '{item.Name}' must be greater than zero."));

            var available = source == OrderSource.Dispatch ? item.DispatchStock : item.StockQuantity;
            if (available < line.Quantity)
            {
                var bucket = source == OrderSource.Dispatch ? "dispatch (truck)" : "inventory";
                return BadRequest(new MessageResponse(
                    $"Insufficient {bucket} stock for '{item.Name}'. Available: {available}, requested: {line.Quantity}."));
            }

            var unitPrice = line.UnitPrice ?? item.UnitPrice;
            order.Items.Add(new OrderItem
            {
                ItemId = item.Id,
                Quantity = line.Quantity,
                UnitPrice = unitPrice,
                LineTotal = unitPrice * line.Quantity,
                ReceivedStatus = ReceivedStatus.Pending
            });
        }

        // Decrement the correct bucket: Inventory -> godown stock; Dispatch -> truck stock.
        foreach (var line in req.Items)
        {
            var item = items[line.ItemId];
            if (source == OrderSource.Dispatch) item.DispatchStock -= line.Quantity;
            else item.StockQuantity -= line.Quantity;
        }

        order.TotalAmount = order.Items.Sum(i => i.LineTotal);
        OrderMath.Recalculate(order);   // no payments yet -> Pending

        _db.Orders.Add(order);
        await _db.SaveChangesAsync();

        var saved = await WithIncludes().FirstAsync(o => o.Id == order.Id);
        return CreatedAtAction(nameof(Get), new { id = order.Id }, Mappers.ToDto(saved));
    }

    [HttpPut("{id:int}")]
    public async Task<ActionResult<OrderDto>> Update(int id, OrderRequest req)
    {
        var order = await _db.Orders
            .Include(o => o.Items)
            .Include(o => o.Payments)
            .FirstOrDefaultAsync(o => o.Id == id);
        if (order is null) return NotFound();
        if (order.SalesmanId != CurrentUserId) return NotOwner();
        if (order.Status == OrderStatus.Completed)
            return BadRequest(new MessageResponse("This order is completed and can no longer be edited."));
        if (req.Items is null || req.Items.Count == 0)
            return BadRequest(new MessageResponse("At least one order item is required."));

        var newSource = req.Source ?? order.Source;

        // Load every item involved (old lines + new lines) so stock can be reconciled.
        var oldLines = order.Items.Select(i => new { i.ItemId, i.Quantity }).ToList();
        var allIds = oldLines.Select(l => l.ItemId).Concat(req.Items.Select(i => i.ItemId)).Distinct().ToList();
        var items = await _db.Items.Where(i => allIds.Contains(i.Id)).ToDictionaryAsync(i => i.Id);

        // 1) Return the original quantities to the order's CURRENT (old) source bucket.
        foreach (var ol in oldLines)
        {
            if (!items.TryGetValue(ol.ItemId, out var it)) continue;
            if (order.Source == OrderSource.Dispatch) it.DispatchStock += ol.Quantity;
            else it.StockQuantity += ol.Quantity;
        }

        // 2) Validate the new lines against the NEW source bucket (post-restore availability).
        //    On failure we return before SaveChanges, so the in-memory restore is never persisted.
        foreach (var line in req.Items)
        {
            if (!items.TryGetValue(line.ItemId, out var item))
                return BadRequest(new MessageResponse($"Item {line.ItemId} not found."));
            if (line.Quantity <= 0)
                return BadRequest(new MessageResponse($"Quantity for '{item.Name}' must be greater than zero."));
            var available = newSource == OrderSource.Dispatch ? item.DispatchStock : item.StockQuantity;
            if (available < line.Quantity)
            {
                var bucket = newSource == OrderSource.Dispatch ? "dispatch (truck)" : "inventory";
                return BadRequest(new MessageResponse(
                    $"Insufficient {bucket} stock for '{item.Name}'. Available: {available}, requested: {line.Quantity}."));
            }
        }

        order.CustomerId = req.CustomerId;
        order.OrderDate = req.OrderDate ?? order.OrderDate;
        order.DeliveryDate = req.DeliveryDate;
        order.Notes = req.Notes;
        if (req.Status is not null) order.Status = req.Status.Value;
        order.Source = newSource;

        // 3) Replace line items and deduct the new quantities from the NEW source bucket.
        _db.OrderItems.RemoveRange(order.Items);
        order.Items.Clear();
        foreach (var line in req.Items)
        {
            var item = items[line.ItemId];
            if (newSource == OrderSource.Dispatch) item.DispatchStock -= line.Quantity;
            else item.StockQuantity -= line.Quantity;

            var unitPrice = line.UnitPrice ?? item.UnitPrice;
            order.Items.Add(new OrderItem
            {
                ItemId = item.Id,
                Quantity = line.Quantity,
                UnitPrice = unitPrice,
                LineTotal = unitPrice * line.Quantity,
                ReceivedStatus = ReceivedStatus.Pending
            });
        }

        order.TotalAmount = order.Items.Sum(i => i.LineTotal);
        OrderMath.Recalculate(order);

        await _db.SaveChangesAsync();
        var saved = await WithIncludes().FirstAsync(o => o.Id == order.Id);
        return Ok(Mappers.ToDto(saved));
    }

    [HttpPut("{id:int}/status")]
    public async Task<ActionResult<OrderDto>> UpdateStatus(int id, UpdateOrderStatusRequest req)
    {
        var order = await _db.Orders.Include(o => o.Payments).FirstOrDefaultAsync(o => o.Id == id);
        if (order is null) return NotFound();
        if (order.SalesmanId != CurrentUserId) return NotOwner();
        if (order.Status == OrderStatus.Completed)
            return BadRequest(new MessageResponse("This order is completed and its status can no longer be changed."));
        order.Status = req.Status;
        OrderMath.Recalculate(order);   // payment status may shift Advance <-> Partial with delivery state
        await _db.SaveChangesAsync();
        var saved = await WithIncludes().FirstAsync(o => o.Id == id);
        return Ok(Mappers.ToDto(saved));
    }

    [HttpPut("{id:int}/delivery-date")]
    public async Task<ActionResult<OrderDto>> UpdateDeliveryDate(int id, UpdateDeliveryDateRequest req)
    {
        var order = await _db.Orders.FindAsync(id);
        if (order is null) return NotFound();
        if (order.SalesmanId != CurrentUserId) return NotOwner();
        order.DeliveryDate = req.DeliveryDate;
        await _db.SaveChangesAsync();
        var saved = await WithIncludes().FirstAsync(o => o.Id == id);
        return Ok(Mappers.ToDto(saved));
    }

    /// <summary>Mark a single order line as received (Completed) / Remaining / Pending.</summary>
    [HttpPut("{id:int}/items/{orderItemId:int}/received-status")]
    public async Task<ActionResult<OrderDto>> UpdateReceivedStatus(
        int id, int orderItemId, UpdateReceivedStatusRequest req)
    {
        if (!await _db.Orders.AnyAsync(o => o.Id == id && o.SalesmanId == CurrentUserId)) return NotOwner();
        var line = await _db.OrderItems.FirstOrDefaultAsync(i => i.Id == orderItemId && i.OrderId == id);
        if (line is null) return NotFound();
        line.ReceivedStatus = req.ReceivedStatus;
        await _db.SaveChangesAsync();

        // If every line is completed, mark the order completed; otherwise keep current status.
        var order = await _db.Orders.Include(o => o.Items).Include(o => o.Payments).FirstAsync(o => o.Id == id);
        if (order.Items.All(i => i.ReceivedStatus == ReceivedStatus.Completed))
            order.Status = OrderStatus.Completed;
        else if (order.Items.Any(i => i.ReceivedStatus == ReceivedStatus.Remaining))
            order.Status = OrderStatus.Remaining;
        OrderMath.Recalculate(order);   // keep payment status (Advance/Partial) in sync with delivery state
        await _db.SaveChangesAsync();

        var saved = await WithIncludes().FirstAsync(o => o.Id == id);
        return Ok(Mappers.ToDto(saved));
    }

    [HttpDelete("{id:int}")]
    public async Task<IActionResult> Delete(int id)
    {
        var order = await _db.Orders.FindAsync(id);
        if (order is null) return NotFound();
        if (order.SalesmanId != CurrentUserId) return NotOwner();
        _db.Orders.Remove(order);
        await _db.SaveChangesAsync();
        return NoContent();
    }

    private async Task<string> GenerateOrderNumberAsync()
    {
        var year = DateTime.UtcNow.Year;
        var count = await _db.Orders.CountAsync(o => o.OrderDate.Year == year);
        return $"ORD-{year}-{(count + 1):D4}";
    }
}
