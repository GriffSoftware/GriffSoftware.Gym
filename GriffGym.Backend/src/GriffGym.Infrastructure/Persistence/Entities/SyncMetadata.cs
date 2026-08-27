namespace GriffGym.Infrastructure.Persistence.Entities;

/// <summary>
/// The columns every record a phone will one day synchronise has to carry.
///
/// These live on the persistence entity, not on the domain model's own fields, because they
/// are maintained by the database layer: <see cref="SyncMetadataInterceptor"/> stamps them on
/// the way out, and the mapper hands them back to the domain object on the way in.
/// </summary>
internal interface ISyncable
{
    /// <summary>
    /// Optimistic concurrency token. Every update writes <c>Version + 1</c> and matches on the
    /// version it read, so a write from a phone holding stale data fails loudly instead of
    /// silently discarding the other device's sets.
    /// </summary>
    int Version { get; set; }

    /// <summary>
    /// Position in one global, strictly increasing sequence of changes, taken from a PostgreSQL
    /// sequence. Everything written in one transaction shares a value, which makes a delta
    /// query ("give me everything above 4 812") return whole, consistent transactions.
    /// </summary>
    long SyncVersion { get; set; }

    DateTimeOffset CreatedAtUtc { get; set; }

    DateTimeOffset UpdatedAtUtc { get; set; }

    /// <summary>
    /// Tombstone. Rows are not physically removed, because a hard delete is invisible to a
    /// device that was offline when it happened.
    /// </summary>
    DateTimeOffset? DeletedAtUtc { get; set; }
}
