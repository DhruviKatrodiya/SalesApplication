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
public class CustomersController : ControllerBase
{
    private readonly AppDbContext _db;
    public CustomersController(AppDbContext db) => _db = db;

    [HttpGet]
    public async Task<ActionResult<IEnumerable<CustomerDto>>> GetAll([FromQuery] string? query)
    {
        var q = _db.Customers.AsQueryable();
        if (!string.IsNullOrWhiteSpace(query))
        {
            var term = query.Trim();
            q = q.Where(c => c.Name.Contains(term)
                || (c.Phone != null && c.Phone.Contains(term))
                || (c.Email != null && c.Email.Contains(term)));
        }
        var list = await q.OrderBy(c => c.Name).Select(c => Mappers.ToDto(c)).ToListAsync();
        return Ok(list);
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<CustomerDto>> Get(int id)
    {
        var c = await _db.Customers.FindAsync(id);
        if (c is null) return NotFound();
        return Ok(Mappers.ToDto(c));
    }

    [HttpPost]
    public async Task<ActionResult<CustomerDto>> Create(CustomerRequest req)
    {
        var c = new Customer { Name = req.Name, Phone = req.Phone, Email = req.Email, Address = req.Address };
        _db.Customers.Add(c);
        await _db.SaveChangesAsync();
        return CreatedAtAction(nameof(Get), new { id = c.Id }, Mappers.ToDto(c));
    }

    [HttpPut("{id:int}")]
    public async Task<ActionResult<CustomerDto>> Update(int id, CustomerRequest req)
    {
        var c = await _db.Customers.FindAsync(id);
        if (c is null) return NotFound();
        c.Name = req.Name;
        c.Phone = req.Phone;
        c.Email = req.Email;
        c.Address = req.Address;
        await _db.SaveChangesAsync();
        return Ok(Mappers.ToDto(c));
    }

    [HttpDelete("{id:int}")]
    public async Task<IActionResult> Delete(int id)
    {
        var c = await _db.Customers.FindAsync(id);
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
        var c = await _db.Customers.FindAsync(id);
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

        var term = query.Trim();
        var matches = await _db.Customers
            .Where(c => c.Name.Contains(term)
                || (c.Phone != null && c.Phone.Contains(term))
                || (c.Email != null && c.Email.Contains(term)))
            .OrderBy(c => c.Name)
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
