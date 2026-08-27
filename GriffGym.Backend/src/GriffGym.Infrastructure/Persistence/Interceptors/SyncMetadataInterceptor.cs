using GriffGym.Application.Abstractions;
using GriffGym.Infrastructure.Persistence.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Diagnostics;

namespace GriffGym.Infrastructure.Persistence.Interceptors;

/// <summary>
/// Stamps timestamps, the concurrency token and the delta-sync cursor onto every synchronised
/// row on its way to the database.
///
/// Doing it here rather than in each repository means there is no path that can forget: a new
/// table, a new use case or a raw <c>SaveChanges</c> in a test all get the same treatment.
///
/// Everything saved in one call shares one <c>SyncVersion</c>, drawn once from a database
/// sequence. That is deliberate — a future delta sync asking for "everything above 4 812" then
/// receives whole transactions, never half of a cycle and half of its program.
/// </summary>
public sealed class SyncMetadataInterceptor(IClock clock) : SaveChangesInterceptor
{
    public override async ValueTask<InterceptionResult<int>> SavingChangesAsync(
        DbContextEventData eventData,
        InterceptionResult<int> result,
        CancellationToken cancellationToken = default)
    {
        if (eventData.Context is not null)
        {
            await StampAsync(eventData.Context, cancellationToken);
        }

        return await base.SavingChangesAsync(eventData, result, cancellationToken);
    }

    private async Task StampAsync(DbContext context, CancellationToken cancellationToken)
    {
        var entries = context.ChangeTracker
            .Entries<ISyncable>()
            .Where(entry => entry.State is EntityState.Added or EntityState.Modified)
            .ToList();

        if (entries.Count == 0)
        {
            return;
        }

        var now = clock.UtcNow;
        var syncVersion = await NextSyncVersionAsync(context, cancellationToken);

        foreach (var entry in entries)
        {
            var entity = entry.Entity;
            entity.SyncVersion = syncVersion;
            entity.UpdatedAtUtc = now;

            if (entry.State == EntityState.Added)
            {
                entity.Version = 1;

                if (entity.CreatedAtUtc == default)
                {
                    entity.CreatedAtUtc = now;
                }
            }
            else
            {
                // EF keeps the original value as the concurrency token, so the UPDATE matches on
                // the revision this request read and writes the next one.
                entity.Version += 1;
            }
        }
    }

    private static async Task<long> NextSyncVersionAsync(
        DbContext context,
        CancellationToken cancellationToken)
    {
        var sequence = context.Model.FindSequence(GriffGymDbContext.SyncVersionSequence)
                       ?? throw new InvalidOperationException(
                           $"Sequence '{GriffGymDbContext.SyncVersionSequence}' is missing from the model.");

        var qualified = string.IsNullOrEmpty(sequence.Schema)
            ? $"\"{sequence.Name}\""
            : $"\"{sequence.Schema}\".\"{sequence.Name}\"";

        // EF1002 warns that raw SQL can carry an injection. The only interpolated value here
        // is a sequence name read out of our own EF model — it never comes from a request — and
        // an identifier cannot be passed as a parameter anyway.
#pragma warning disable EF1002
        var values = await context.Database
            .SqlQueryRaw<long>($"SELECT nextval('{qualified}') AS \"Value\"")
            .ToListAsync(cancellationToken);
#pragma warning restore EF1002

        return values[0];
    }
}
