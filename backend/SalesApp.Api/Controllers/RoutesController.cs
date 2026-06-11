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
public class RoutesController : OwnedControllerBase
{
    private readonly AppDbContext _db;
    public RoutesController(AppDbContext db) => _db = db;

    [HttpGet]
    public async Task<ActionResult<PagedResult<RouteDto>>> GetAll(
        [FromQuery] string? search, [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        var uid = CurrentUserId;
        var query = _db.Routes.Where(r => r.UserId == uid);
        if (!string.IsNullOrWhiteSpace(search))
        {
            var term = search.Trim();
            query = query.Where(r => r.Name.Contains(term));
        }

        var ordered = query.OrderByDescending(r => r.CreatedAt).ThenByDescending(r => r.Id);
        var total = await ordered.CountAsync();
        var items = await ordered
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .Select(r => new RouteDto(r.Id, r.Name, r.Description, r.Customers.Count))
            .ToListAsync();

        return Ok(new PagedResult<RouteDto>(items, total, page, pageSize));
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<RouteDto>> Get(int id)
    {
        var r = await _db.Routes.Include(x => x.Customers)
            .FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (r is null) return NotFound();
        return Ok(new RouteDto(r.Id, r.Name, r.Description, r.Customers.Count));
    }

    [HttpPost]
    public async Task<ActionResult<RouteDto>> Create(RouteRequest req)
    {
        var r = new DeliveryRoute { UserId = CurrentUserId, Name = req.Name, Description = req.Description };
        _db.Routes.Add(r);
        await _db.SaveChangesAsync();
        return CreatedAtAction(nameof(Get), new { id = r.Id }, new RouteDto(r.Id, r.Name, r.Description, 0));
    }

    [HttpPut("{id:int}")]
    public async Task<ActionResult<RouteDto>> Update(int id, RouteRequest req)
    {
        var r = await _db.Routes.FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (r is null) return NotFound();
        r.Name = req.Name;
        r.Description = req.Description;
        await _db.SaveChangesAsync();
        return Ok(new RouteDto(r.Id, r.Name, r.Description, 0));
    }

    [HttpDelete("{id:int}")]
    public async Task<IActionResult> Delete(int id)
    {
        var r = await _db.Routes.FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (r is null) return NotFound();
        _db.Routes.Remove(r);   // customers on this route are unassigned (RouteId -> null)
        await _db.SaveChangesAsync();
        return NoContent();
    }
}
