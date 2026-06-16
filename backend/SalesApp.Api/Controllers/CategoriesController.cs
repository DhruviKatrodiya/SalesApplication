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
public class CategoriesController : OwnedControllerBase
{
    private readonly AppDbContext _db;
    public CategoriesController(AppDbContext db) => _db = db;

    [HttpGet]
    public async Task<ActionResult<PagedResult<CategoryDto>>> GetAll(
        [FromQuery] string? search, [FromQuery] string? active, [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        var uid = CurrentUserId;
        var query = _db.Categories.Where(c => c.UserId == uid);
        if (active != "all") query = query.Where(c => c.IsActive == (active != "inactive"));
        if (!string.IsNullOrWhiteSpace(search))
        {
            var term = search.Trim();
            query = query.Where(c => c.Name.Contains(term));
        }

        var total = await query.CountAsync();
        var items = await query
            .OrderByDescending(c => c.CreatedAt).ThenByDescending(c => c.Id)
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .Select(c => new CategoryDto(c.Id, c.Name, c.Description, c.SubCategories.Count, c.IsActive))
            .ToListAsync();

        return Ok(new PagedResult<CategoryDto>(items, total, page, pageSize));
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<CategoryDto>> Get(int id)
    {
        var c = await _db.Categories.Include(x => x.SubCategories)
            .FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (c is null) return NotFound();
        return Ok(new CategoryDto(c.Id, c.Name, c.Description, c.SubCategories.Count));
    }

    [HttpPost]
    public async Task<ActionResult<CategoryDto>> Create(CategoryRequest req)
    {
        var c = new Category { UserId = CurrentUserId, Name = req.Name, Description = req.Description };
        _db.Categories.Add(c);
        await _db.SaveChangesAsync();
        return CreatedAtAction(nameof(Get), new { id = c.Id },
            new CategoryDto(c.Id, c.Name, c.Description, 0));
    }

    [HttpPut("{id:int}")]
    public async Task<ActionResult<CategoryDto>> Update(int id, CategoryRequest req)
    {
        var c = await _db.Categories.FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (c is null) return NotFound();
        c.Name = req.Name;
        c.Description = req.Description;
        await _db.SaveChangesAsync();
        return Ok(new CategoryDto(c.Id, c.Name, c.Description, 0));
    }

    [HttpDelete("{id:int}")]
    public async Task<IActionResult> Delete(int id)
    {
        var c = await _db.Categories.FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (c is null) return NotFound();
        c.IsActive = false;
        await _db.SaveChangesAsync();
        return NoContent();
    }

    [HttpPut("{id:int}/activate")]
    public async Task<IActionResult> Activate(int id)
    {
        var c = await _db.Categories.FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (c is null) return NotFound();
        c.IsActive = true;
        await _db.SaveChangesAsync();
        return NoContent();
    }
}
