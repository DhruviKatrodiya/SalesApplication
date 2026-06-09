using System.Security.Cryptography;
using Microsoft.EntityFrameworkCore;
using SalesApp.Api.Data;
using SalesApp.Api.Models;

namespace SalesApp.Api.Services;

public class OtpService
{
    private readonly AppDbContext _db;
    private const int ExpiryMinutes = 10;

    public OtpService(AppDbContext db) => _db = db;

    public async Task<string> GenerateAsync(string email)
    {
        // 6-digit numeric OTP
        var otp = RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");

        // Invalidate previous unused codes for this email
        var existing = await _db.PasswordResetOtps
            .Where(o => o.Email == email && !o.IsUsed)
            .ToListAsync();
        foreach (var e in existing) e.IsUsed = true;

        _db.PasswordResetOtps.Add(new PasswordResetOtp
        {
            Email = email,
            OtpHash = BCrypt.Net.BCrypt.HashPassword(otp),
            ExpiresAt = DateTime.UtcNow.AddMinutes(ExpiryMinutes),
            IsUsed = false
        });
        await _db.SaveChangesAsync();
        return otp;
    }

    public async Task<bool> VerifyAsync(string email, string otp, bool consume)
    {
        var record = await _db.PasswordResetOtps
            .Where(o => o.Email == email && !o.IsUsed && o.ExpiresAt > DateTime.UtcNow)
            .OrderByDescending(o => o.CreatedAt)
            .FirstOrDefaultAsync();

        if (record is null) return false;
        if (!BCrypt.Net.BCrypt.Verify(otp, record.OtpHash)) return false;

        if (consume)
        {
            record.IsUsed = true;
            await _db.SaveChangesAsync();
        }
        return true;
    }
}
