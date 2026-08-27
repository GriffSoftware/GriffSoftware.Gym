namespace GriffGym.Domain.Common;

/// <summary>
/// Base for everything the mobile app will one day synchronise.
///
/// The identifier is a <see cref="Guid"/> rather than a database sequence because the phone is
/// allowed to invent it: a lifter can train offline for months and only then create an
/// account, and the rows they already own must keep the identity they were born with. Nothing
/// in this model assumes the server saw a record first.
///
/// Sync metadata lives here rather than only in the persistence layer because for an
/// offline-first product "which revision of this record am I holding?" is part of the contract
/// between phone and server, not an incidental storage detail. The values are assigned by the
/// persistence layer and carried through unchanged; see docs/ARCHITECTURE.md.
/// </summary>
public abstract class Entity
{
    protected Entity(Guid id, DateTimeOffset createdAtUtc, DateTimeOffset updatedAtUtc)
    {
        DomainException.Require(id != Guid.Empty, "An entity needs a non-empty identifier.");

        Id = id;
        CreatedAtUtc = createdAtUtc;
        UpdatedAtUtc = updatedAtUtc;
    }

    public Guid Id { get; }

    public DateTimeOffset CreatedAtUtc { get; }

    public DateTimeOffset UpdatedAtUtc { get; private set; }

    /// <summary>
    /// Revision counter, incremented on every persisted change. Clients send the version they
    /// hold when writing; a mismatch is a lost update and is reported as a conflict rather than
    /// silently overwriting somebody else's phone.
    /// </summary>
    public int Version { get; private set; }

    /// <summary>
    /// Position in one global, strictly increasing sequence of changes. The cursor a future
    /// delta sync will page from: "give me everything above 4 812".
    /// </summary>
    public long SyncVersion { get; private set; }

    /// <summary>
    /// Tombstone. A row is never physically removed while another device might still be
    /// holding it, because a hard delete is invisible to a client that was offline when it
    /// happened.
    /// </summary>
    public DateTimeOffset? DeletedAtUtc { get; private set; }

    public bool IsDeleted => DeletedAtUtc is not null;

    /// <summary>
    /// Called by the persistence layer: when a stored row is rehydrated, and again after a save,
    /// so the aggregate in hand carries the revision that was actually written. Without the
    /// second call, a response would report the version the request started with — and a client
    /// that sent that back as its expected version would be told it was out of date.
    /// </summary>
    public void ApplySyncMetadata(
        int version,
        long syncVersion,
        DateTimeOffset updatedAtUtc,
        DateTimeOffset? deletedAtUtc)
    {
        Version = version;
        SyncVersion = syncVersion;
        UpdatedAtUtc = updatedAtUtc;
        DeletedAtUtc = deletedAtUtc;
    }

    public void MarkDeleted(DateTimeOffset now)
    {
        if (DeletedAtUtc is not null)
        {
            return;
        }

        DeletedAtUtc = now;
        Touch(now);
    }

    /// <summary>Records that something about this entity changed at <paramref name="at"/>.</summary>
    protected void Touch(DateTimeOffset at) => UpdatedAtUtc = at;

    public override bool Equals(object? obj) =>
        obj is Entity other && other.GetType() == GetType() && other.Id == Id;

    public override int GetHashCode() => HashCode.Combine(GetType(), Id);
}
