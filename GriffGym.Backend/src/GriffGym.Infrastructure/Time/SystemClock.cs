using GriffGym.Application.Abstractions;

namespace GriffGym.Infrastructure.Time;

public sealed class SystemClock : IClock
{
    /// <summary>
    /// Always UTC. Every timestamp in the schema is <c>timestamptz</c> and every column is named
    /// <c>...AtUtc</c>, so there is exactly one place a local time could sneak in, and it is not
    /// here.
    /// </summary>
    public DateTimeOffset UtcNow => DateTimeOffset.UtcNow;
}
