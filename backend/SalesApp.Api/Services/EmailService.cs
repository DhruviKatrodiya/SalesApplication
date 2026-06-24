using Microsoft.Extensions.Options;

namespace SalesApp.Api.Services;

/// <summary>
/// Application-facing email API. Callers enqueue messages; the actual SMTP send happens
/// off the request thread in <see cref="EmailBackgroundService"/>.
/// </summary>
public interface IEmailService
{
    /// <summary>True when SMTP is configured (used to pre-validate before queueing invoice emails).</summary>
    bool IsConfigured { get; }

    /// <summary>Queues a password-reset OTP email.</summary>
    void QueueOtp(string toEmail, string otp);

    /// <summary>Queues an invoice email with the PDF attached.</summary>
    void QueueInvoice(string toEmail, string customerName, string orderNumber, byte[] pdf);
}

public class EmailService : IEmailService
{
    private readonly IEmailQueue _queue;
    private readonly SmtpSettings _smtp;

    public EmailService(IEmailQueue queue, IOptions<SmtpSettings> smtp)
    {
        _queue = queue;
        _smtp = smtp.Value;
    }

    public bool IsConfigured => _smtp.IsConfigured;

    public void QueueOtp(string toEmail, string otp)
    {
        var body = $"Your One-Time Password (OTP) for resetting your Sales App password is: {otp}\n\n" +
                   "This code is valid for 10 minutes. If you did not request this, please ignore this email.";
        _queue.Enqueue(new EmailMessage(toEmail, "Your password reset code", body));
    }

    public void QueueInvoice(string toEmail, string customerName, string orderNumber, byte[] pdf)
    {
        var body = $"Dear {customerName},\n\nPlease find attached the invoice for your order {orderNumber}.\n\n" +
                   "Thank you for your business.\nSales Application";
        _queue.Enqueue(new EmailMessage(
            toEmail, $"Invoice for order {orderNumber}", body, pdf, $"Invoice-{orderNumber}.pdf"));
    }
}
