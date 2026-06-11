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
    public DbSet<DeliveryRoute> Routes => Set<DeliveryRoute>();
    public DbSet<Order> Orders => Set<Order>();
    public DbSet<OrderItem> OrderItems => Set<OrderItem>();
    public DbSet<Payment> Payments => Set<Payment>();
    public DbSet<Dispatch> Dispatches => Set<Dispatch>();
    public DbSet<DispatchItem> DispatchItems => Set<DispatchItem>();
    public DbSet<AppUser> Users => Set<AppUser>();
    public DbSet<PasswordResetOtp> PasswordResetOtps => Set<PasswordResetOtp>();
    public DbSet<DispatchDraft> DispatchDrafts => Set<DispatchDraft>();
    public DbSet<StockRequest> StockRequests => Set<StockRequest>();
    public DbSet<StockRequestItem> StockRequestItems => Set<StockRequestItem>();
    public DbSet<StockRequestPayment> StockRequestPayments => Set<StockRequestPayment>();

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
        b.Entity<DispatchDraft>().HasIndex(x => x.UserId).IsUnique();   // one draft per user (each line carries its truck)

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

        // Order owner (salesperson); deleting a user just unassigns their orders.
        b.Entity<Order>()
            .HasOne(x => x.Salesman).WithMany()
            .HasForeignKey(x => x.SalesmanId).OnDelete(DeleteBehavior.SetNull);

        // A customer optionally belongs to a delivery route; deleting a route
        // just unassigns its customers (sets RouteId to null).
        b.Entity<Customer>()
            .HasOne(x => x.Route).WithMany(r => r.Customers)
            .HasForeignKey(x => x.RouteId).OnDelete(DeleteBehavior.SetNull);

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

        // Stock requests ("My Orders")
        b.Entity<StockRequest>()
            .HasOne(x => x.Salesman).WithMany()
            .HasForeignKey(x => x.SalesmanId).OnDelete(DeleteBehavior.SetNull);
        b.Entity<StockRequestItem>()
            .HasOne(x => x.StockRequest).WithMany(x => x.Items)
            .HasForeignKey(x => x.StockRequestId).OnDelete(DeleteBehavior.Cascade);
        b.Entity<StockRequestItem>()
            .HasOne(x => x.Item).WithMany()
            .HasForeignKey(x => x.ItemId).OnDelete(DeleteBehavior.Restrict);
        b.Entity<StockRequestPayment>()
            .HasOne(x => x.StockRequest).WithMany(x => x.Payments)
            .HasForeignKey(x => x.StockRequestId).OnDelete(DeleteBehavior.Cascade);

        b.Entity<StockRequest>().Property(x => x.TotalAmount).HasPrecision(18, 2);
        b.Entity<StockRequest>().Property(x => x.PaidAmount).HasPrecision(18, 2);
        b.Entity<StockRequest>().Property(x => x.RemainingAmount).HasPrecision(18, 2);
        b.Entity<StockRequestItem>().Property(x => x.UnitPrice).HasPrecision(18, 2);
        b.Entity<StockRequestItem>().Property(x => x.LineTotal).HasPrecision(18, 2);
        b.Entity<StockRequestPayment>().Property(x => x.Amount).HasPrecision(18, 2);
    }
}
