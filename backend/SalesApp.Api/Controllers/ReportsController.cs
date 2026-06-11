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
public class ReportsController : ControllerBase
{
    private readonly AppDbContext _db;
    public ReportsController(AppDbContext db) => _db = db;

    private static readonly string[] MonthNames =
        { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

    /// <summary>Yearly report broken down by month, with optional single-month focus.</summary>
    [HttpGet("monthly")]
    public async Task<ActionResult<ReportSummary>> Monthly(
        [FromQuery] int year, [FromQuery] int? month,
        [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        var query = _db.Orders.Where(o => o.OrderDate.Year == year);
        if (month is not null) query = query.Where(o => o.OrderDate.Month == month);
        var orders = await query.ToListAsync();

        var rows = orders
            .GroupBy(o => o.OrderDate.Month)
            .OrderBy(g => g.Key)
            .Select(g => new ReportRow(
                MonthNames[g.Key - 1], g.Count(),
                g.Sum(o => o.TotalAmount), g.Sum(o => o.PaidAmount), g.Sum(o => o.RemainingAmount)))
            .ToList();

        var period = month is null ? $"{year}" : $"{MonthNames[month.Value - 1]} {year}";
        return Ok(Build(period, orders, rows, page, pageSize));
    }

    /// <summary>Daily report for a given month, broken down by day.</summary>
    [HttpGet("daily")]
    public async Task<ActionResult<ReportSummary>> Daily(
        [FromQuery] int year, [FromQuery] int month,
        [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        var orders = await _db.Orders
            .Where(o => o.OrderDate.Year == year && o.OrderDate.Month == month)
            .ToListAsync();

        var rows = orders
            .GroupBy(o => o.OrderDate.Day)
            .OrderBy(g => g.Key)
            .Select(g => new ReportRow(
                $"{g.Key:D2} {MonthNames[month - 1]}", g.Count(),
                g.Sum(o => o.TotalAmount), g.Sum(o => o.PaidAmount), g.Sum(o => o.RemainingAmount)))
            .ToList();

        return Ok(Build($"{MonthNames[month - 1]} {year}", orders, rows, page, pageSize));
    }

    /// <summary>Yearly report broken down by month.</summary>
    [HttpGet("yearly")]
    public async Task<ActionResult<ReportSummary>> Yearly(
        [FromQuery] int year, [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        var orders = await _db.Orders.Where(o => o.OrderDate.Year == year).ToListAsync();
        var rows = orders
            .GroupBy(o => o.OrderDate.Month)
            .OrderBy(g => g.Key)
            .Select(g => new ReportRow(
                MonthNames[g.Key - 1], g.Count(),
                g.Sum(o => o.TotalAmount), g.Sum(o => o.PaidAmount), g.Sum(o => o.RemainingAmount)))
            .ToList();
        return Ok(Build($"{year}", orders, rows, page, pageSize));
    }

    /// <summary>Per-customer summary: pending vs delivered counts and outstanding balances.</summary>
    [HttpGet("by-customer")]
    public async Task<ActionResult<PagedResult<object>>> ByCustomer(
        [FromQuery] int page = 1, [FromQuery] int pageSize = 5)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;

        var grouped = _db.Orders
            .Include(o => o.Customer)
            .GroupBy(o => new { o.CustomerId, o.Customer!.Name })
            .Select(g => new
            {
                customerId = g.Key.CustomerId,
                customerName = g.Key.Name,
                totalOrders = g.Count(),
                pendingOrders = g.Count(o => o.Status != OrderStatus.Delivered && o.Status != OrderStatus.Completed),
                deliveredOrders = g.Count(o => o.Status == OrderStatus.Delivered || o.Status == OrderStatus.Completed),
                totalAmount = g.Sum(o => o.TotalAmount),
                paidAmount = g.Sum(o => o.PaidAmount),
                remainingAmount = g.Sum(o => o.RemainingAmount)
            })
            .OrderBy(x => x.customerName);

        var total = await grouped.CountAsync();
        var items = await grouped.Skip((page - 1) * pageSize).Take(pageSize).ToListAsync();
        return Ok(new PagedResult<object>(items.Cast<object>().ToList(), total, page, pageSize));
    }

    private static ReportSummary Build(string period, List<Order> orders, List<ReportRow> rows, int page, int pageSize)
    {
        page = page < 1 ? 1 : page;
        pageSize = pageSize is < 1 or > 1000 ? 5 : pageSize;
        var pagedRows = rows.Skip((page - 1) * pageSize).Take(pageSize).ToList();

        // Summary stats are always computed over the whole period; only the breakdown rows are paged.
        return new(
            period,
            orders.Count,
            orders.Sum(o => o.TotalAmount),
            orders.Sum(o => o.PaidAmount),
            orders.Sum(o => o.RemainingAmount),
            orders.Count(o => o.Status != OrderStatus.Delivered && o.Status != OrderStatus.Completed),
            orders.Count(o => o.Status == OrderStatus.Delivered || o.Status == OrderStatus.Completed),
            rows.Count,
            pagedRows);
    }
}
