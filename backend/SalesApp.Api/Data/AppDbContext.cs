using Microsoft.EntityFrameworkCore;
using SalesApp.Api.Models;

namespace SalesApp.Api.Data;

public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }

    public DbSet<Category> Categories => Set<Category>();
    public DbSet<SubCategory> SubCategories => Set<SubCategory>();
    public DbSet<Item> Items => Set<Item>();
    public DbSet<Customer> Customers => Set<Customer>();
    public DbSet<Order> Orders => Set<Order>();
    public DbSet<OrderItem> OrderItems => Set<OrderItem>();
    public DbSet<Payment> Payments => Set<Payment>();
    public DbSet<Dispatch> Dispatches => Set<Dispatch>();
    public DbSet<DispatchItem> DispatchItems => Set<DispatchItem>();
    public DbSet<AppUser> Users => Set<AppUser>();
    public DbSet<PasswordResetOtp> PasswordResetOtps => Set<PasswordResetOtp>();

    protected override void OnModelCreating(ModelBuilder b)
    {
        base.OnModelCreating(b);

        // Decimal precision
        b.Entity<Item>().Property(x => x.UnitPrice).HasPrecision(18, 2);
        b.Entity<Order>().Property(x => x.TotalAmount).HasPrecision(18, 2);
        b.Entity<Order>().Property(x => x.PaidAmount).HasPrecision(18, 2);
        b.Entity<Order>().Property(x => x.RemainingAmount).HasPrecision(18, 2);
        b.Entity<OrderItem>().Property(x => x.UnitPrice).HasPrecision(18, 2);
        b.Entity<OrderItem>().Property(x => x.LineTotal).HasPrecision(18, 2);
        b.Entity<Payment>().Property(x => x.Amount).HasPrecision(18, 2);

        // Unique email for app user
        b.Entity<AppUser>().HasIndex(x => x.Email).IsUnique();
        b.Entity<Order>().HasIndex(x => x.OrderNumber).IsUnique();
        b.Entity<PasswordResetOtp>().HasIndex(x => x.Email);

        // Relationships
        b.Entity<SubCategory>()
            .HasOne(x => x.Category).WithMany(x => x.SubCategories)
            .HasForeignKey(x => x.CategoryId).OnDelete(DeleteBehavior.Cascade);

        b.Entity<Item>()
            .HasOne(x => x.SubCategory).WithMany(x => x.Items)
            .HasForeignKey(x => x.SubCategoryId).OnDelete(DeleteBehavior.Cascade);

        b.Entity<Order>()
            .HasOne(x => x.Customer).WithMany(x => x.Orders)
            .HasForeignKey(x => x.CustomerId).OnDelete(DeleteBehavior.Restrict);

        b.Entity<OrderItem>()
            .HasOne(x => x.Order).WithMany(x => x.Items)
            .HasForeignKey(x => x.OrderId).OnDelete(DeleteBehavior.Cascade);
        b.Entity<OrderItem>()
            .HasOne(x => x.Item).WithMany()
            .HasForeignKey(x => x.ItemId).OnDelete(DeleteBehavior.Restrict);

        b.Entity<Payment>()
            .HasOne(x => x.Order).WithMany(x => x.Payments)
            .HasForeignKey(x => x.OrderId).OnDelete(DeleteBehavior.Cascade);

        b.Entity<DispatchItem>()
            .HasOne(x => x.Dispatch).WithMany(x => x.Items)
            .HasForeignKey(x => x.DispatchId).OnDelete(DeleteBehavior.Cascade);
        b.Entity<DispatchItem>()
            .HasOne(x => x.Item).WithMany()
            .HasForeignKey(x => x.ItemId).OnDelete(DeleteBehavior.Restrict);
    }
}
