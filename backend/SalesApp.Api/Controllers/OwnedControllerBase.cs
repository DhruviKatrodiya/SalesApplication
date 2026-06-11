using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;

namespace SalesApp.Api.Controllers;

/// <summary>Base for controllers whose data is owned per salesperson.</summary>
public abstract class OwnedControllerBase : ControllerBase
{
    protected int CurrentUserId =>
        int.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier) ?? "0");
}
