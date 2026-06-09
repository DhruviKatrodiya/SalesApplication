using Microsoft.EntityFrameworkCore;
using SalesApp.Api.Models;

namespace SalesApp.Api.Data;

public static class DbSeeder
{
    public const string DefaultEmail = "sales@salesapp.com";
    public const string DefaultPassword = "Sales@123";

    public static async Task SeedAsync(AppDbContext db)
    {
        await db.Database.MigrateAsync();

        if (!await db.Users.AnyAsync())
        {
            db.Users.Add(new AppUser
            {
                FullName = "Sales Person",
                Email = DefaultEmail,
                Phone = "0000000000",
                PasswordHash = BCrypt.Net.BCrypt.HashPassword(DefaultPassword)
            });
            await db.SaveChangesAsync();
        }
    }
}
