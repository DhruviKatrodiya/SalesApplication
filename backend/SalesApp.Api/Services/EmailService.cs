using MailKit.Net.Smtp;
using MailKit.Security;
using Microsoft.Extensions.Options;
using MimeKit;

namespace SalesApp.Api.Services;

public interface IEmailService
{
    Task SendOtpAsync(string toEmail, string otp);
    /// <summary>Emails the invoice PDF to the customer. Throws InvalidOperationException if SMTP isn't configured.</summary>
    Task SendInvoiceAsync(string toEmail, string customerName, string orderNumber, byte[] pdf);
}

public class EmailService : IEmailService
{
    private readonly SmtpSettings _smtp;
    private readonly ILogger<EmailService> _logger;

    public EmailService(IOptions<SmtpSettings> smtp, ILogger<EmailService> logger)
    {
        _smtp = smtp.Value;
        _logger = logger;
    }

    public async Task SendOtpAsync(string toEmail, string otp)
    {
        var subject = "Your password reset code";
        var body = $"Your One-Time Password (OTP) for resetting your Sales App password is: {otp}\n\n" +
                   "This code is valid for 10 minutes. If you did not request this, please ignore this email.";

        if (!_smtp.IsConfigured)
        {
            // Dev fallback: SMTP not configured — log the OTP so the flow is still testable.
            _logger.LogWarning("SMTP not configured. OTP for {Email} is: {Otp}", toEmail, otp);
            return;
        }

        var message = new MimeMessage();
        message.From.Add(new MailboxAddress(_smtp.FromName,
            string.IsNullOrWhiteSpace(_smtp.FromEmail) ? _smtp.User : _smtp.FromEmail));
        message.To.Add(MailboxAddress.Parse(toEmail));
        message.Subject = subject;
        message.Body = new TextPart("plain") { Text = body };

        using var client = new SmtpClient();
        var socketOption = _smtp.UseSsl ? SecureSocketOptions.StartTls : SecureSocketOptions.Auto;
        await client.ConnectAsync(_smtp.Host, _smtp.Port, socketOption);
        await client.AuthenticateAsync(_smtp.User, _smtp.Password);
        await client.SendAsync(message);
        await client.DisconnectAsync(true);

        _logger.LogInformation("OTP email sent to {Email}", toEmail);
    }

    public async Task SendInvoiceAsync(string toEmail, string customerName, string orderNumber, byte[] pdf)
    {
        if (!_smtp.IsConfigured)
            throw new InvalidOperationException("Email is not configured on the server.");

        var message = new MimeMessage();
        message.From.Add(new MailboxAddress(_smtp.FromName,
            string.IsNullOrWhiteSpace(_smtp.FromEmail) ? _smtp.User : _smtp.FromEmail));
        message.To.Add(MailboxAddress.Parse(toEmail));
        message.Subject = $"Invoice for order {orderNumber}";

        var builder = new BodyBuilder
        {
            TextBody = $"Dear {customerName},\n\nPlease find attached the invoice for your order {orderNumber}.\n\n" +
                       "Thank you for your business.\nSales Application"
        };
        builder.Attachments.Add($"Invoice-{orderNumber}.pdf", pdf, new ContentType("application", "pdf"));
        message.Body = builder.ToMessageBody();

        using var client = new SmtpClient();
        var socketOption = _smtp.UseSsl ? SecureSocketOptions.StartTls : SecureSocketOptions.Auto;
        await client.ConnectAsync(_smtp.Host, _smtp.Port, socketOption);
        await client.AuthenticateAsync(_smtp.User, _smtp.Password);
        await client.SendAsync(message);
        await client.DisconnectAsync(true);

        _logger.LogInformation("Invoice {Order} emailed to {Email}", orderNumber, toEmail);
    }
}
