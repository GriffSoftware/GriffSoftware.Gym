namespace GriffGym.Application.Abstractions;

/// <summary>
/// Wall-clock time, injected rather than read from <see cref="DateTimeOffset.UtcNow"/> so that
/// token expiry, cycle completion and session duration can be tested without waiting.
/// </summary>
public interface IClock
{
    DateTimeOffset UtcNow { get; }
}

/// <summary>
/// Mints identifiers for rows the server creates on its own — refresh tokens, and any record
/// a client did not supply an id for.
///
/// Sequential (v7) GUIDs, so that primary key inserts stay clustered rather than scattering
/// writes across the whole index the way v4 does.
/// </summary>
public interface IIdentifierFactory
{
    Guid NewId();
}

public sealed class SequentialIdentifierFactory : IIdentifierFactory
{
    public Guid NewId() => Guid.CreateVersion7();
}
