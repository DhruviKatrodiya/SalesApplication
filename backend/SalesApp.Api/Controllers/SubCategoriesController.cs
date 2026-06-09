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
public class SubCategoriesController : ControllerBase
{
    private readonly AppDbContext _db;
    public SubCategoriesController(AppDbContext db) => _db = db;

    [HttpGet]
    public async Task<ActionResult<IEnumerable<SubCategoryDto>>> GetAll([FromQuery] int? categoryId)
    {
        var query = _db.SubCategories.Include(s => s.Category).AsQueryable();
        if (categoryId is not null) query = query.Where(s => s.CategoryId == categoryId);

        var list = await query
            .OrderBy(s => s.Name)
            .Select(s => new SubCategoryDto(
                s.Id, s.CategoryId, s.Category!.Name, s.Name, s.Description, s.Items.Count))
            .ToListAsync();
        return Ok(list);
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<SubCategoryDto>> Get(int id)
    {
        var s = await _db.SubCategories.Include(x => x.Category).Include(x => x.Items)
            .FirstOrDefaultAsync(x => x.Id == id);
        if (s is null) return NotFound();
        return Ok(new SubCategoryDto(s.Id, s.CategoryId, s.Category!.Name, s.Name, s.Description, s.Items.Count));
    }

    [HttpPost]
    public async Task<ActionResult<SubCategoryDto>> Create(SubCategoryRequest req)
    {
        if (!await _db.Categories.AnyAsync(c => c.Id == req.CategoryId))
            return BadRequest(new MessageResponse("Category not found."));

        var s = new SubCategory { CategoryId = req.CategoryId, Name = req.Name, Description = req.Description };
        _db.SubCategories.Add(s);
        await _db.SaveChangesAsync();
        await _db.Entry(s).Reference(x => x.Category).LoadAsync();
        return CreatedAtAction(nameof(Get), new { id = s.Id },
            new SubCategoryDto(s.Id, s.CategoryId, s.Category!.Name, s.Name, s.Description, 0));
    }

    [HttpPut("{id:int}")]
    public async Task<ActionResult<SubCategoryDto>> Update(int id, SubCategoryRequest req)
    {
        var s = await _db.SubCategories.FindAsync(id);
        if (s is null) return NotFound();
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
        var s = await _db.SubCategories.FindAsync(id);
        if (s is null) return NotFound();
        _db.SubCategories.Remove(s);
        await _db.SaveChangesAsync();
        return NoContent();
    }
}
