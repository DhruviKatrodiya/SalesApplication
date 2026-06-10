namespace SalesApp.Api.Models;

/// <summary>
/// An unsaved "new dispatch" cart, persisted per user so the selected items
/// survive navigating away from the page before recording the dispatch.
/// One row per user; the line items are stored as JSON.
/// </summary>
public class DispatchDraft
{
    public int Id { get; set; }
    public int UserId { get; set; }
    public string? TruckLabel { get; set; }
    public string? Notes { get; set; }
    public string ItemsJson { get; set; } = "[]";
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
