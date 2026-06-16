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
public class SubCategoriesController : OwnedControllerBase
{
    private readonly AppDbContext _db;
    public SubCategoriesController(AppDbContext db) => _db = db;

    [HttpGet]
    public async Task<ActionResult<PagedResult<SubCategoryDto>>> GetAll(
        [FromQuery] int? categoryId, [FromQuery] string? search, [FromQuery] string? active,
        [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        var uid = CurrentUserId;
        var query = _db.SubCategories.Include(s => s.Category).Where(s => s.UserId == uid);
        if (active != "all") query = query.Where(s => s.IsActive == (active != "inactive"));
        if (categoryId is not null) query = query.Where(s => s.CategoryId == categoryId);
        if (!string.IsNullOrWhiteSpace(search))
        {
            var term = search.Trim();
            query = query.Where(s => s.Name.Contains(term));
        }

        var total = await query.CountAsync();
        var items = await query
            .OrderByDescending(s => s.CreatedAt).ThenByDescending(s => s.Id)
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .Select(s => new SubCategoryDto(
                s.Id, s.CategoryId, s.Category!.Name, s.Name, s.Description, s.Items.Count, s.IsActive))
            .ToListAsync();

        return Ok(new PagedResult<SubCategoryDto>(items, total, page, pageSize));
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<SubCategoryDto>> Get(int id)
    {
        var s = await _db.SubCategories.Include(x => x.Category).Include(x => x.Items)
            .FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (s is null) return NotFound();
        return Ok(new SubCategoryDto(s.Id, s.CategoryId, s.Category!.Name, s.Name, s.Description, s.Items.Count));
    }

    [HttpPost]
    public async Task<ActionResult<SubCategoryDto>> Create(SubCategoryRequest req)
    {
        var uid = CurrentUserId;
        if (!await _db.Categories.AnyAsync(c => c.Id == req.CategoryId && c.UserId == uid))
            return BadRequest(new MessageResponse("Category not found."));

        var s = new SubCategory { UserId = uid, CategoryId = req.CategoryId, Name = req.Name, Description = req.Description };
        _db.SubCategories.Add(s);
        await _db.SaveChangesAsync();
        await _db.Entry(s).Reference(x => x.Category).LoadAsync();
        return CreatedAtAction(nameof(Get), new { id = s.Id },
            new SubCategoryDto(s.Id, s.CategoryId, s.Category!.Name, s.Name, s.Description, 0));
    }

    [HttpPut("{id:int}")]
    public async Task<ActionResult<SubCategoryDto>> Update(int id, SubCategoryRequest req)
    {
        var uid = CurrentUserId;
        var s = await _db.SubCategories.FirstOrDefaultAsync(x => x.Id == id && x.UserId == uid);
        if (s is null) return NotFound();
        if (!await _db.Categories.AnyAsync(c => c.Id == req.CategoryId && c.UserId == uid))
            return BadRequest(new MessageResponse("Category not found."));
        s.CategoryId = req.CategoryId;
        s.Name = req.Name;
        s.Description = req.Description;
        await _db.SaveChangesAsync();
        await _db.Entry(s).Reference(x => x.Category).LoadAsync();
        return Ok(new SubCategoryDto(s.Id, s.CategoryId, s.Category!.Name, s.Name, s.Description, 0));
    }

    [HttpDelete("{id:int}")]
    public async Task<IActionResult> Delete(int id)
    {
        var s = await _db.SubCategories.FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (s is null) return NotFound();
        s.IsActive = false;
        await _db.SaveChangesAsync();
        return NoContent();
    }

    [HttpPut("{id:int}/activate")]
    public async Task<IActionResult> Activate(int id)
    {
        var s = await _db.SubCategories.FirstOrDefaultAsync(x => x.Id == id && x.UserId == CurrentUserId);
        if (s is null) return NotFound();
        s.IsActive = true;
        await _db.SaveChangesAsync();
        return NoContent();
    }
}
