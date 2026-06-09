using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SalesApp.Api.Data;
using SalesApp.Api.DTOs;

namespace SalesApp.Api.Controllers;

[ApiController]
[Authorize]
[Route("api/[controller]")]
public class ProfileController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly IWebHostEnvironment _env;
    public ProfileController(AppDbContext db, IWebHostEnvironment env)
    {
        _db = db;
        _env = env;
    }

    private int CurrentUserId =>
        int.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier) ?? "0");

    [HttpGet("me")]
    public async Task<ActionResult<UserDto>> Me()
    {
        var user = await _db.Users.FindAsync(CurrentUserId);
        if (user is null) return NotFound();
        return Ok(new UserDto(user.Id, user.FullName, user.Email, user.Phone, user.ProfileImagePath));
    }

    [HttpPut]
    public async Task<ActionResult<UserDto>> UpdateProfile(UpdateProfileRequest req)
    {
        var user = await _db.Users.FindAsync(CurrentUserId);
        if (user is null) return NotFound();

        var email = (req.Email ?? string.Empty).Trim();
        if (string.IsNullOrWhiteSpace(email))
            return BadRequest(new MessageResponse("Email is required."));

        // Ensure the email isn't already used by another account.
        var emailTaken = await _db.Users.AnyAsync(u => u.Id != user.Id && u.Email == email);
        if (emailTaken)
            return BadRequest(new MessageResponse("That email address is already in use."));

        user.FullName = req.FullName;
        user.Email = email;
        user.Phone = req.Phone;
        user.ProfileImagePath = req.ProfileImagePath;
        await _db.SaveChangesAsync();

        return Ok(new UserDto(user.Id, user.FullName, user.Email, user.Phone, user.ProfileImagePath));
    }

    /// <summary>Uploads a profile image and returns its public URL (to be saved via PUT /profile).</summary>
    [HttpPost("upload-image")]
    [RequestSizeLimit(5 * 1024 * 1024)]
    public async Task<ActionResult<object>> UploadImage(IFormFile file)
    {
        if (file is null || file.Length == 0)
            return BadRequest(new MessageResponse("No file uploaded."));
        if (file.Length > 5 * 1024 * 1024)
            return BadRequest(new MessageResponse("Image must be under 5 MB."));

        var allowed = new[] { ".png", ".jpg", ".jpeg", ".gif", ".webp" };
        var ext = Path.GetExtension(file.FileName).ToLowerInvariant();
        if (!allowed.Contains(ext))
            return BadRequest(new MessageResponse("Only image files (png, jpg, jpeg, gif, webp) are allowed."));

        var webRoot = _env.WebRootPath ?? Path.Combine(Directory.GetCurrentDirectory(), "wwwroot");
        var uploadsDir = Path.Combine(webRoot, "uploads", "profile");
        Directory.CreateDirectory(uploadsDir);

        var fileName = $"{Guid.NewGuid():N}{ext}";
        var fullPath = Path.Combine(uploadsDir, fileName);
        await using (var stream = System.IO.File.Create(fullPath))
            await file.CopyToAsync(stream);

        var url = $"{Request.Scheme}://{Request.Host}/uploads/profile/{fileName}";
        return Ok(new { url });
    }

    [HttpPost("change-password")]
    public async Task<ActionResult<MessageResponse>> ChangePassword(ChangePasswordRequest req)
    {
        var user = await _db.Users.FindAsync(CurrentUserId);
        if (user is null) return NotFound();

        if (!BCrypt.Net.BCrypt.Verify(req.CurrentPassword, user.PasswordHash))
            return BadRequest(new MessageResponse("Current password is incorrect."));

        user.PasswordHash = BCrypt.Net.BCrypt.HashPassword(req.NewPassword);
        await _db.SaveChangesAsync();
        return Ok(new MessageResponse("Password changed successfully."));
    }
}
