namespace SalesApp.Api.Models;

public class AppUser
{
    public int Id { get; set; }
    public string FullName { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string PasswordHash { get; set; } = string.Empty;
    public string? Phone { get; set; }
    public string? ProfileImagePath { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

public class PasswordResetOtp
{
    public int Id { get; set; }
    public string Email { get; set; } = string.Empty;
    public string OtpHash { get; set; } = string.Empty;
    public DateTime ExpiresAt { get; set; }
    public bool IsUsed { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
