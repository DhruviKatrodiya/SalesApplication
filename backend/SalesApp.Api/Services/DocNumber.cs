using System.Text.RegularExpressions;

namespace SalesApp.Api.Services;

/// <summary>
/// Generates and validates document numbers in the format PREFIX-YY-MM-DD-000001
/// (e.g. ORD-26-06-15-000001, REQ-26-06-15-000001). The 6-digit sequence resets
/// to 000001 at the start of each calendar month.
/// </summary>
public static class DocNumber
{
    public static string Format(string prefix, DateTime date, int sequence) =>
        $"{prefix}-{date:yy-MM-dd}-{sequence:D6}";

    public static bool IsValid(string? value, string prefix) =>
        value is not null && Regex.IsMatch(value, $@"^{Regex.Escape(prefix)}-\d{{2}}-\d{{2}}-\d{{2}}-\d{{6}}$");
}
