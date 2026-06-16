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
    private readonly IInvoiceService _invoices;
    private readonly IEmailService _email;
    private readonly InventoryService _inv;
    private readonly ILogger<OrdersController> _logger;
    public OrdersController(AppDbContext db, IInvoiceService invoices, IEmailService email, InventoryService inv, ILogger<OrdersController> logger)
    {
        _db = db;
        _invoices = invoices;
        _email = email;
        _inv = inv;
        _logger = logger;
    }

    private int CurrentUserId =>
        int.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier) ?? "0");

    private ObjectResult NotOwner() =>
        StatusCode(403, new MessageResponse("You can only manage your own orders."));

    private IQueryable<Order> WithIncludes() =>
        _db.Orders
            .Include(o => o.Customer)
            .Include(o => o.Truck)
            .Include(o => o.Items).ThenInclude(i => i.Item)
            .Include(o => o.Payments);

    [HttpGet]
    public async Task<ActionResult<PagedResult<OrderDto>>> GetAll(
        [FromQuery] string? orderNumber, [FromQuery] string? customer, [FromQuery] DateTime? orderDate,
        [FromQuery] OrderStatus? status, [FromQuery] PaymentStatus? paymentStatus,
        [FromQuery] int? customerId, [FromQuery] bool mine = false, [FromQuery] string? active = null,
        [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        // Data is isolated per salesperson — always scope to the current user's own orders.
        var uid = CurrentUserId;
        var query = WithIncludes().Where(o => o.SalesmanId == uid);
        if (active != "all") query = query.Where(o => o.IsActive == (active != "inactive"));
        if (customerId is not null) query = query.Where(o => o.CustomerId == customerId);
        if (!string.IsNullOrWhiteSpace(orderNumber))
        {
            var t = orderNumber.Trim();
            query = query.Where(o => o.OrderNumber.Contains(t));
        }
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

        var ordered = query.OrderByDescending(o => o.CreatedAt).ThenByDescending(o => o.Id);
        var total = await ordered.CountAsync();
        var items = (await ordered.Skip((page - 1) * pageSize).Take(pageSize).ToListAsync())
            .Select(Mappers.ToDto).ToList();

        return Ok(new PagedResult<OrderDto>(items, total, page, pageSize));
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<OrderDto>> Get(int id)
    {
        var o = await WithIncludes().FirstOrDefaultAsync(x => x.Id == id && x.SalesmanId == CurrentUserId);
        if (o is null) return NotFound();
        return Ok(Mappers.ToDto(o));
    }

    [HttpPost]
    public async Task<ActionResult<OrderDto>> Create(OrderRequest req)
    {
        var uid = CurrentUserId;
        if (!await _db.Customers.AnyAsync(c => c.Id == req.CustomerId && c.UserId == uid))
            return BadRequest(new MessageResponse("Customer not found."));
        if (req.Items is null || req.Items.Count == 0)
            return BadRequest(new MessageResponse("At least one order item is required."));

        var itemIds = req.Items.Select(i => i.ItemId).ToList();
        var items = await _db.Items.Where(i => itemIds.Contains(i.Id) && i.UserId == uid).ToDictionaryAsync(i => i.Id);

        var source = req.Source ?? OrderSource.Inventory;

        // For a dispatch order, resolve the chosen truck and load its per-item stock.
        Truck? truck = null;
        Dictionary<int, TruckStock> truckStock = new();
        if (source == OrderSource.Dispatch)
        {
            if (req.TruckId is null)
                return BadRequest(new MessageResponse("Select a truck for a dispatch (truck) order."));
            truck = await _db.Trucks.Include(t => t.Stock)
                .FirstOrDefaultAsync(t => t.Id == req.TruckId && t.UserId == uid);
            if (truck is null) return BadRequest(new MessageResponse("Truck not found."));
            truckStock = truck.Stock.ToDictionary(s => s.ItemId);
        }
        int TruckAvailable(int itemId) => truckStock.TryGetValue(itemId, out var s) ? s.Quantity : 0;

        var order = new Order
        {
            OrderNumber = await GenerateOrderNumberAsync(),
            CustomerId = req.CustomerId,
            SalesmanId = CurrentUserId,   // owned by the salesperson who creates it
            TruckId = truck?.Id,          // dispatch orders are tied to a truck
            OrderDate = req.OrderDate ?? DateTime.UtcNow,
            DeliveryDate = req.DeliveryDate,
            Notes = req.Notes,
            Status = OrderStatus.Pending,
            Source = source
        };

        // Validate existence, quantity, and available stock for every line — against the
        // correct bucket: Inventory orders draw from godown stock; Dispatch orders draw
        // from the SELECTED TRUCK's own stock only.
        foreach (var line in req.Items)
        {
            if (!items.TryGetValue(line.ItemId, out var item))
                return BadRequest(new MessageResponse($"Item {line.ItemId} not found."));
            if (line.Quantity <= 0)
                return BadRequest(new MessageResponse($"Quantity for '{item.Name}' must be greater than zero."));

            var available = source == OrderSource.Dispatch ? TruckAvailable(line.ItemId) : item.StockQuantity;
            if (available < line.Quantity)
            {
                var bucket = source == OrderSource.Dispatch ? $"truck '{truck!.Name}'" : "inventory";
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

        // Dispatch -> deduct the truck's stock now. Inventory -> consumed via FIFO after the order is
        // saved (needs the order id for the movement ledger / reversal on edit).
        foreach (var line in req.Items)
        {
            var item = items[line.ItemId];
            if (source == OrderSource.Dispatch)
            {
                truckStock[line.ItemId].Quantity -= line.Quantity;
                item.DispatchStock -= line.Quantity;
            }
        }

        order.TotalAmount = order.Items.Sum(i => i.LineTotal);
        OrderMath.Recalculate(order);   // no payments yet -> Pending

        // Validate the generated number's prefix/date/sequence format before persisting.
        if (!DocNumber.IsValid(order.OrderNumber, "ORD"))
            return StatusCode(500, new MessageResponse("Failed to generate a valid order number."));

        _db.Orders.Add(order);
        await _db.SaveChangesAsync();

        if (source == OrderSource.Inventory)
            foreach (var line in req.Items)
                await _inv.ConsumeFifoAsync(items[line.ItemId], line.Quantity, "Order", order.Id, order.OrderNumber);

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
        var uid = CurrentUserId;
        if (order.SalesmanId != uid) return NotOwner();
        if (order.Status == OrderStatus.Completed)
            return BadRequest(new MessageResponse("This order is completed and can no longer be edited."));
        if (req.Items is null || req.Items.Count == 0)
            return BadRequest(new MessageResponse("At least one order item is required."));
        if (!await _db.Customers.AnyAsync(c => c.Id == req.CustomerId && c.UserId == uid))
            return BadRequest(new MessageResponse("Customer not found."));

        var newSource = req.Source ?? order.Source;

        // Load every item involved (old lines + new lines) so stock can be reconciled.
        var oldLines = order.Items.Select(i => new { i.ItemId, i.Quantity }).ToList();
        var allIds = oldLines.Select(l => l.ItemId).Concat(req.Items.Select(i => i.ItemId)).Distinct().ToList();
        var items = await _db.Items.Where(i => allIds.Contains(i.Id) && i.UserId == uid).ToDictionaryAsync(i => i.Id);

        // Resolve the old truck (the order's current one) and the new truck (the request's),
        // so dispatch stock can be moved between the correct truck ledgers.
        Truck? oldTruck = null, newTruck = null;
        if (order.Source == OrderSource.Dispatch && order.TruckId is not null)
            oldTruck = await _db.Trucks.Include(t => t.Stock).FirstOrDefaultAsync(t => t.Id == order.TruckId && t.UserId == uid);
        if (newSource == OrderSource.Dispatch)
        {
            if (req.TruckId is null)
                return BadRequest(new MessageResponse("Select a truck for a dispatch (truck) order."));
            newTruck = await _db.Trucks.Include(t => t.Stock).FirstOrDefaultAsync(t => t.Id == req.TruckId && t.UserId == uid);
            if (newTruck is null) return BadRequest(new MessageResponse("Truck not found."));
        }
        // Find (or create) a truck's stock row for an item.
        static TruckStock Row(Truck t, int itemId)
        {
            var s = t.Stock.FirstOrDefault(x => x.ItemId == itemId);
            if (s is null) { s = new TruckStock { ItemId = itemId, Quantity = 0 }; t.Stock.Add(s); }
            return s;
        }

        // 1) Return the original quantities to the order's CURRENT (old) source bucket / truck.
        //    Inventory: reverse the FIFO consumption (restores the exact batches). Dispatch: restore truck.
        if (order.Source == OrderSource.Inventory)
            await _inv.ReverseAsync("Order", order.Id);
        else
            foreach (var ol in oldLines)
            {
                if (!items.TryGetValue(ol.ItemId, out var it)) continue;
                if (oldTruck is not null) Row(oldTruck, ol.ItemId).Quantity += ol.Quantity;
                it.DispatchStock += ol.Quantity;
            }

        // 2) Validate the new lines against the NEW source bucket / truck (post-restore availability).
        //    On failure we return before SaveChanges, so the in-memory restore is never persisted.
        foreach (var line in req.Items)
        {
            if (!items.TryGetValue(line.ItemId, out var item))
                return BadRequest(new MessageResponse($"Item {line.ItemId} not found."));
            if (line.Quantity <= 0)
                return BadRequest(new MessageResponse($"Quantity for '{item.Name}' must be greater than zero."));
            var available = newSource == OrderSource.Dispatch
                ? (newTruck!.Stock.FirstOrDefault(s => s.ItemId == line.ItemId)?.Quantity ?? 0)
                : item.StockQuantity;
            if (available < line.Quantity)
            {
                var bucket = newSource == OrderSource.Dispatch ? $"truck '{newTruck!.Name}'" : "inventory";
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
        order.TruckId = newTruck?.Id;

        // 3) Replace line items and deduct the new quantities from the NEW source bucket / truck.
        _db.OrderItems.RemoveRange(order.Items);
        order.Items.Clear();
        foreach (var line in req.Items)
        {
            var item = items[line.ItemId];
            // Dispatch deducts the truck now; inventory is FIFO-consumed after save.
            if (newSource == OrderSource.Dispatch)
            {
                Row(newTruck!, line.ItemId).Quantity -= line.Quantity;
                item.DispatchStock -= line.Quantity;
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

        order.TotalAmount = order.Items.Sum(i => i.LineTotal);
        OrderMath.Recalculate(order);

        await _db.SaveChangesAsync();

        if (newSource == OrderSource.Inventory)
            foreach (var line in req.Items)
                await _inv.ConsumeFifoAsync(items[line.ItemId], line.Quantity, "Order", order.Id, order.OrderNumber);

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
        order.IsActive = false;
        await _db.SaveChangesAsync();
        return NoContent();
    }

    [HttpPut("{id:int}/activate")]
    public async Task<IActionResult> Activate(int id)
    {
        var order = await _db.Orders.FindAsync(id);
        if (order is null) return NotFound();
        if (order.SalesmanId != CurrentUserId) return NotOwner();
        order.IsActive = true;
        await _db.SaveChangesAsync();
        return NoContent();
    }

    /// <summary>Generates and downloads the order's item-wise invoice as a PDF.</summary>
    [HttpGet("{id:int}/invoice")]
    public async Task<IActionResult> Invoice(int id)
    {
        var order = await WithIncludes().FirstOrDefaultAsync(o => o.Id == id && o.SalesmanId == CurrentUserId);
        if (order is null) return NotFound(new MessageResponse("Order not found."));
        if (order.Items.Count == 0) return BadRequest(new MessageResponse("This order has no items to invoice."));
        try
        {
            var pdf = _invoices.Generate(order);
            return File(pdf, "application/pdf", $"Invoice-{order.OrderNumber}.pdf");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Invoice generation failed for order {Id}", id);
            return StatusCode(500, new MessageResponse("Could not generate the invoice PDF."));
        }
    }

    /// <summary>Generates the invoice PDF and emails it to the customer's registered email address.</summary>
    [HttpPost("{id:int}/invoice/email")]
    public async Task<IActionResult> EmailInvoice(int id)
    {
        var order = await WithIncludes().FirstOrDefaultAsync(o => o.Id == id && o.SalesmanId == CurrentUserId);
        if (order is null) return NotFound(new MessageResponse("Order not found."));
        if (order.Items.Count == 0) return BadRequest(new MessageResponse("This order has no items to invoice."));

        var email = order.Customer?.Email;
        if (string.IsNullOrWhiteSpace(email))
            return BadRequest(new MessageResponse("This customer has no email address on file. Add one and try again."));

        byte[] pdf;
        try { pdf = _invoices.Generate(order); }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Invoice generation failed for order {Id}", id);
            return StatusCode(500, new MessageResponse("Could not generate the invoice PDF."));
        }

        try
        {
            await _email.SendInvoiceAsync(email!, order.Customer!.Name, order.OrderNumber, pdf);
            return Ok(new MessageResponse($"Invoice emailed to {email}."));
        }
        catch (InvalidOperationException ex)
        {
            return BadRequest(new MessageResponse(ex.Message));   // e.g. SMTP not configured
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to email invoice for order {Id}", id);
            return StatusCode(502, new MessageResponse("Failed to send the invoice email. Please try again later."));
        }
    }

    private async Task<string> GenerateOrderNumberAsync()
    {
        var now = DateTime.UtcNow;
        // Monthly sequence: count this month's orders so the number resets to 000001 each month.
        var seq = await _db.Orders.CountAsync(o => o.CreatedAt.Year == now.Year && o.CreatedAt.Month == now.Month) + 1;
        var number = DocNumber.Format("ORD", now, seq);
        // Guarantee uniqueness even against legacy/imported rows.
        while (await _db.Orders.AnyAsync(o => o.OrderNumber == number))
            number = DocNumber.Format("ORD", now, ++seq);
        return number;
    }
}
