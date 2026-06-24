using MailKit.Net.Smtp;
using MailKit.Security;
using Microsoft.Extensions.Options;
using MimeKit;

namespace SalesApp.Api.Services;

/// <summary>Low-level SMTP sender used by the background worker. Sends a single message.</summary>
public interface IEmailSender
{
    Task SendAsync(EmailMessage message, CancellationToken ct = default);
}

public class SmtpEmailSender : IEmailSender
{
    private readonly SmtpSettings _smtp;
    private readonly ILogger<SmtpEmailSender> _logger;

    public SmtpEmailSender(IOptions<SmtpSettings> smtp, ILogger<SmtpEmailSender> logger)
    {
        _smtp = smtp.Value;
        _logger = logger;
    }

    public async Task SendAsync(EmailMessage message, CancellationToken ct = default)
    {
        if (!_smtp.IsConfigured)
        {
            // Dev fallback: SMTP not configured — log the message (the OTP body contains the code)
            // so the flow stays testable without a real mail server.
            _logger.LogWarning("SMTP not configured. Email to {To} not sent.\nSubject: {Subject}\n{Body}",
                message.To, message.Subject, message.Body);
            return;
        }

        var mime = new MimeMessage();
        mime.From.Add(new MailboxAddress(_smtp.FromName,
            string.IsNullOrWhiteSpace(_smtp.FromEmail) ? _smtp.User : _smtp.FromEmail));
        mime.To.Add(MailboxAddress.Parse(message.To));
        mime.Subject = message.Subject;

        if (message.Attachment is { Length: > 0 })
        {
            var builder = new BodyBuilder { TextBody = message.Body };
            builder.Attachments.Add(message.AttachmentName ?? "attachment.pdf",
                message.Attachment, new ContentType("application", "pdf"));
            mime.Body = builder.ToMessageBody();
        }
        else
        {
            mime.Body = new TextPart("plain") { Text = message.Body };
        }

        using var client = new SmtpClient();
        var socketOption = _smtp.UseSsl ? SecureSocketOptions.StartTls : SecureSocketOptions.Auto;
        await client.ConnectAsync(_smtp.Host, _smtp.Port, socketOption, ct);
        await client.AuthenticateAsync(_smtp.User, _smtp.Password, ct);
        await client.SendAsync(mime, ct);
        await client.DisconnectAsync(true, ct);

        _logger.LogInformation("Email sent to {To} (subject: {Subject})", message.To, message.Subject);
    }
}
