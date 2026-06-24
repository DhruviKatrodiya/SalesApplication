using System.Threading.Channels;

namespace SalesApp.Api.Services;

/// <summary>A single email to be delivered by the background worker.</summary>
public record EmailMessage(
    string To,
    string Subject,
    string Body,
    byte[]? Attachment = null,
    string? AttachmentName = null);

/// <summary>In-memory producer/consumer queue for outgoing emails.</summary>
public interface IEmailQueue
{
    void Enqueue(EmailMessage message);
    ChannelReader<EmailMessage> Reader { get; }
}

public class EmailQueue : IEmailQueue
{
    // Unbounded channel: enqueuing never blocks the web request.
    private readonly Channel<EmailMessage> _channel =
        Channel.CreateUnbounded<EmailMessage>(new UnboundedChannelOptions { SingleReader = true });

    public void Enqueue(EmailMessage message) => _channel.Writer.TryWrite(message);

    public ChannelReader<EmailMessage> Reader => _channel.Reader;
}
