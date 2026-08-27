using GriffGym.Domain.Common;
using GriffGym.Infrastructure.Persistence.Entities;

namespace GriffGym.Infrastructure.Persistence.Repositories;

/// <summary>
/// Carries state between the domain aggregates this request touched and their rows: down onto
/// the records before EF works out what to write, and back up afterwards.
/// </summary>
internal interface IPersistenceFlush
{
    void Flush();

    void RefreshAfterSave();
}

/// <summary>
/// Keeps a domain aggregate and the row it came from side by side for the life of a request.
///
/// EF Core tracks the persistence record; the use cases mutate the domain object. Something has
/// to carry changes from one to the other, and doing it once at the end — rather than making
/// every use case remember to call <c>repository.Update(aggregate)</c> — means a forgotten call
/// cannot silently drop a lifter's sets.
///
/// Returning the cached aggregate on a repeat read matters too: two reads of the same workout
/// inside one request must hand back the same object, or one of them ends up writing over the
/// other's changes.
/// </summary>
internal abstract class TrackedRepository<TDomain, TRecord> : IPersistenceFlush
    where TDomain : Entity
    where TRecord : class
{
    private readonly Dictionary<Guid, TrackedPair> _tracked = [];

    private readonly record struct TrackedPair(TDomain Domain, TRecord Record);

    public void Flush()
    {
        foreach (var pair in _tracked.Values)
        {
            Apply(pair.Domain, pair.Record);

            if (pair.Record is ISyncable syncable)
            {
                // Copying the aggregate's own last-changed time up to the root row is what makes
                // the root's revision move when something inside it does.
                //
                // A logged set changes a set_log row, not the workout_session row — so without
                // this, EF sees the root as untouched, its version never advances, and optimistic
                // concurrency on a workout silently protects nothing. The domain touches the root
                // whenever anything in the aggregate changes, so this is the signal to use.
                syncable.UpdatedAtUtc = pair.Domain.UpdatedAtUtc;
            }
        }
    }

    /// <summary>
    /// Brings each aggregate back in step with the row that was just written, so a response
    /// reports the revision the database actually holds rather than the one the request read.
    /// </summary>
    public void RefreshAfterSave()
    {
        foreach (var pair in _tracked.Values)
        {
            if (pair.Record is ISyncable syncable)
            {
                pair.Domain.ApplySyncMetadata(
                    syncable.Version,
                    syncable.SyncVersion,
                    syncable.UpdatedAtUtc,
                    syncable.DeletedAtUtc);
            }
        }
    }

    protected abstract void Apply(TDomain domain, TRecord record);

    protected TDomain Track(TDomain domain, TRecord record)
    {
        _tracked[domain.Id] = new TrackedPair(domain, record);
        return domain;
    }

    protected TDomain? Cached(Guid id) =>
        _tracked.TryGetValue(id, out var pair) ? pair.Domain : null;

    /// <summary>
    /// Materialises a row, unless this request already holds the aggregate it maps to.
    /// </summary>
    protected TDomain Materialise(TRecord record, Guid id, Func<TRecord, TDomain> toDomain) =>
        Cached(id) ?? Track(toDomain(record), record);
}
