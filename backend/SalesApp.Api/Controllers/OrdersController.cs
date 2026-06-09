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

    private IQueryable<Order> WithIncludes() =>
        _db.Orders
            .Include(o => o.Customer)
            .Include(o => o.Items).ThenInclude(i => i.Item)
            .Include(o => o.Payments);

    [HttpGet]
    public async Task<ActionResult<IEnumerable<OrderDto>>> GetAll(
        [FromQuery] int? customerId, [FromQuery] OrderStatus? status)
    {
        var query = WithIncludes();
        if (customerId is not null) query = query.Where(o => o.CustomerId == customerId);
        if (status is not null) query = query.Where(o => o.Status == status);

        var list = await query.OrderByDescending(o => o.OrderDate).ToListAsync();
        return Ok(list.Select(Mappers.ToDto));
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

        var order = new Order
        {
            OrderNumber = await GenerateOrderNumberAsync(),
            CustomerId = req.CustomerId,
            OrderDate = req.OrderDate ?? DateTime.UtcNow,
            DeliveryDate = req.DeliveryDate,
            Notes = req.Notes,
            Status = OrderStatus.Pending
        };

        foreach (var line in req.Items)
        {
            if (!items.TryGetValue(line.ItemId, out var item))
                return BadRequest(new MessageResponse($"Item {line.ItemId} not found."));
            if (line.Quantity <= 0)
                return BadRequest(new MessageResponse($"Quantity for '{item.Name}' must be greater than zero."));

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
        if (req.Items is null || req.Items.Count == 0)
            return BadRequest(new MessageResponse("At least one order item is required."));

        var itemIds = req.Items.Select(i => i.ItemId).ToList();
        var items = await _db.Items.Where(i => itemIds.Contains(i.Id)).ToDictionaryAsync(i => i.Id);

        order.CustomerId = req.CustomerId;
        order.OrderDate = req.OrderDate ?? order.OrderDate;
        order.DeliveryDate = req.DeliveryDate;
        order.Notes = req.Notes;

        // Replace line items
        _db.OrderItems.RemoveRange(order.Items);
        order.Items.Clear();
        foreach (var line in req.Items)
        {
            if (!items.TryGetValue(line.ItemId, out var item))
                return BadRequest(new MessageResponse($"Item {line.ItemId} not found."));
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
        var order = await _db.Orders.FindAsync(id);
        if (order is null) return NotFound();
        order.Status = req.Status;
        await _db.SaveChangesAsync();
        var saved = await WithIncludes().FirstAsync(o => o.Id == id);
        return Ok(Mappers.ToDto(saved));
    }

    [HttpPut("{id:int}/delivery-date")]
    public async Task<ActionResult<OrderDto>> UpdateDeliveryDate(int id, UpdateDeliveryDateRequest req)
    {
        var order = await _db.Orders.FindAsync(id);
        if (order is null) return NotFound();
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
        var line = await _db.OrderItems.FirstOrDefaultAsync(i => i.Id == orderItemId && i.OrderId == id);
        if (line is null) return NotFound();
        line.ReceivedStatus = req.ReceivedStatus;
        await _db.SaveChangesAsync();

        // If every line is completed, mark the order completed; otherwise keep current status.
        var order = await _db.Orders.Include(o => o.Items).FirstAsync(o => o.Id == id);
        if (order.Items.All(i => i.ReceivedStatus == ReceivedStatus.Completed))
            order.Status = OrderStatus.Completed;
        else if (order.Items.Any(i => i.ReceivedStatus == ReceivedStatus.Remaining))
            order.Status = OrderStatus.Remaining;
        await _db.SaveChangesAsync();

        var saved = await WithIncludes().FirstAsync(o => o.Id == id);
        return Ok(Mappers.ToDto(saved));
    }

    [HttpDelete("{id:int}")]
    public async Task<IActionResult> Delete(int id)
    {
        var order = await _db.Orders.FindAsync(id);
        if (order is null) return NotFound();
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
