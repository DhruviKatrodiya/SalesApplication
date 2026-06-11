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
public class CustomersController : OwnedControllerBase
{
    private readonly AppDbContext _db;
    public CustomersController(AppDbContext db) => _db = db;

    [HttpGet]
    public async Task<ActionResult<PagedResult<CustomerDto>>> GetAll(
        [FromQuery] string? name, [FromQuery] string? phone, [FromQuery] int? routeId,
        [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        var uid = CurrentUserId;
        var q = _db.Customers.Include(c => c.Route).Where(c => c.UserId == uid);
        if (!string.IsNullOrWhiteSpace(name))
        {
            var t = name.Trim();
            q = q.Where(c => c.Name.Contains(t));
        }
        if (!string.IsNullOrWhiteSpace(phone))
        {
            var t = phone.Trim();
            q = q.Where(c => c.Phone != null && c.Phone.Contains(t));
        }
        if (routeId is not null) q = q.Where(c => c.RouteId == routeId);

        var ordered = q.OrderByDescending(c => c.CreatedAt).ThenByDescending(c => c.Id);
        var total = await ordered.CountAsync();
        var pageItems = (await ordered.Skip((page - 1) * pageSize).Take(pageSize).ToListAsync())
            .Select(Mappers.ToDto).ToList();

        return Ok(new PagedResult<CustomerDto>(pageItems, total, page, pageSize));
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<CustomerDto>> Get(int id)
    {
        var c = await _db.Customers.Include(x => x.Route).FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (c is null) return NotFound();
        return Ok(Mappers.ToDto(c));
    }

    [HttpPost]
    public async Task<ActionResult<CustomerDto>> Create(CustomerRequest req)
    {
        var uid = CurrentUserId;
        if (req.RouteId is not null && !await _db.Routes.AnyAsync(r => r.Id == req.RouteId && r.UserId == uid))
            return BadRequest(new MessageResponse("Route not found."));

        var c = new Customer { UserId = uid, Name = req.Name, Phone = req.Phone, Email = req.Email, Address = req.Address, RouteId = req.RouteId };
        _db.Customers.Add(c);
        await _db.SaveChangesAsync();
        await _db.Entry(c).Reference(x => x.Route).LoadAsync();
        return CreatedAtAction(nameof(Get), new { id = c.Id }, Mappers.ToDto(c));
    }

    [HttpPut("{id:int}")]
    public async Task<ActionResult<CustomerDto>> Update(int id, CustomerRequest req)
    {
        var uid = CurrentUserId;
        if (req.RouteId is not null && !await _db.Routes.AnyAsync(r => r.Id == req.RouteId && r.UserId == uid))
            return BadRequest(new MessageResponse("Route not found."));

        var c = await _db.Customers.FirstOrDefaultAsync(x => x.Id == id && x.UserId == uid);
        if (c is null) return NotFound();
        c.Name = req.Name;
        c.Phone = req.Phone;
        c.Email = req.Email;
        c.Address = req.Address;
        c.RouteId = req.RouteId;
        await _db.SaveChangesAsync();
        await _db.Entry(c).Reference(x => x.Route).LoadAsync();
        return Ok(Mappers.ToDto(c));
    }

    [HttpDelete("{id:int}")]
    public async Task<IActionResult> Delete(int id)
    {
        var c = await _db.Customers.FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (c is null) return NotFound();
        _db.Customers.Remove(c);
        await _db.SaveChangesAsync();
        return NoContent();
    }

    /// <summary>
    /// Full customer profile: payment totals, pending/delivered counts, and every order with items.
    /// This powers the "search ABC -> see everything" view.
    /// </summary>
    [HttpGet("{id:int}/details")]
    public async Task<ActionResult<CustomerSearchResult>> Details(int id)
    {
        var c = await _db.Customers.Include(x => x.Route).FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (c is null) return NotFound();
        return Ok(await BuildDetails(c));
    }

    /// <summary>
    /// Search by name/phone/email and return full details for the best match (e.g. "ABC").
    /// </summary>
    [HttpGet("search")]
    public async Task<ActionResult<IEnumerable<CustomerSearchResult>>> Search([FromQuery] string query)
    {
        if (string.IsNullOrWhiteSpace(query))
            return BadRequest(new MessageResponse("query is required."));

        var uid = CurrentUserId;
        var term = query.Trim();
        var matches = await _db.Customers
            .Include(c => c.Route)
            .Where(c => c.UserId == uid && (c.Name.Contains(term)
                || (c.Phone != null && c.Phone.Contains(term))
                || (c.Email != null && c.Email.Contains(term))))
            .OrderByDescending(c => c.CreatedAt).ThenByDescending(c => c.Id)
            .ToListAsync();

        var results = new List<CustomerSearchResult>();
        foreach (var c in matches)
            results.Add(await BuildDetails(c));
        return Ok(results);
    }

    private async Task<CustomerSearchResult> BuildDetails(Customer c)
    {
        var orders = await _db.Orders
            .Where(o => o.CustomerId == c.Id)
            .Include(o => o.Customer)
            .Include(o => o.Items).ThenInclude(i => i.Item)
            .OrderByDescending(o => o.OrderDate)
            .ToListAsync();

        var totalOrdered = orders.Sum(o => o.TotalAmount);
        var totalPaid = orders.Sum(o => o.PaidAmount);
        var totalRemaining = orders.Sum(o => o.RemainingAmount);

        string overall = totalPaid <= 0 ? "Pending"
            : totalRemaining <= 0 ? "Paid"
            : "Advance";

        var pending = orders.Count(o => o.Status != OrderStatus.Delivered && o.Status != OrderStatus.Completed);
        var delivered = orders.Count(o => o.Status == OrderStatus.Delivered || o.Status == OrderStatus.Completed);

        return new CustomerSearchResult(
            Mappers.ToDto(c), totalOrdered, totalPaid, totalRemaining, overall,
            pending, delivered, orders.Select(Mappers.ToDto).ToList());
    }
}
