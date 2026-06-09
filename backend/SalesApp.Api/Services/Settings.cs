namespace SalesApp.Api.Services;

public class JwtSettings
{
    public string Issuer { get; set; } = "SalesApp";
    public string Audience { get; set; } = "SalesAppClient";
    public string Key { get; set; } = string.Empty;
    public int ExpiryMinutes { get; set; } = 480;
}

public class SmtpSettings
{
    public string Host { get; set; } = string.Empty;
    public int Port { get; set; } = 587;
    public bool UseSsl { get; set; } = true;
    public string User { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
    public string FromName { get; set; } = "Sales App";
    public string FromEmail { get; set; } = string.Empty;

    public bool IsConfigured =>
        !string.IsNullOrWhiteSpace(Host) &&
        !string.IsNullOrWhiteSpace(User) &&
        !string.IsNullOrWhiteSpace(Password);
}
