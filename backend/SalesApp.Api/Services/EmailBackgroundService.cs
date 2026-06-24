namespace SalesApp.Api.Services;

/// <summary>
/// Long-running background worker that drains the email queue and sends each message
/// via SMTP, off the web-request thread. Failed sends are retried a few times.
/// </summary>
public class EmailBackgroundService : BackgroundService
{
    private readonly IEmailQueue _queue;
    private readonly IEmailSender _sender;
    private readonly ILogger<EmailBackgroundService> _logger;

    private const int MaxAttempts = 3;
    private static readonly TimeSpan RetryDelay = TimeSpan.FromSeconds(10);

    public EmailBackgroundService(IEmailQueue queue, IEmailSender sender, ILogger<EmailBackgroundService> logger)
    {
        _queue = queue;
        _sender = sender;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _logger.LogInformation("Email background service started.");

        await foreach (var message in _queue.Reader.ReadAllAsync(stoppingToken))
        {
            await SendWithRetryAsync(message, stoppingToken);
        }
    }

    private async Task SendWithRetryAsync(EmailMessage message, CancellationToken ct)
    {
        for (var attempt = 1; attempt <= MaxAttempts; attempt++)
        {
            try
            {
                await _sender.SendAsync(message, ct);
                return;
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                return; // app shutting down
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to send email to {To} (attempt {Attempt}/{Max})",
                    message.To, attempt, MaxAttempts);

                if (attempt < MaxAttempts)
                {
                    try { await Task.Delay(RetryDelay, ct); }
                    catch (OperationCanceledException) { return; }
                }
                else
                {
                    _logger.LogError("Giving up on email to {To} after {Max} attempts.", message.To, MaxAttempts);
                }
            }
        }
    }
}
