using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Common;
using GriffGym.Infrastructure.Persistence.Repositories;
using Microsoft.EntityFrameworkCore;

namespace GriffGym.Infrastructure.Persistence;

internal sealed class UnitOfWork(
    GriffGymDbContext context,
    IEnumerable<IPersistenceFlush> repositories) : IUnitOfWork
{
    public async Task SaveChangesAsync(CancellationToken cancellationToken)
    {
        FlushAggregates();

        try
        {
            await context.SaveChangesAsync(cancellationToken);
        }
        catch (DbUpdateConcurrencyException exception)
        {
            // Another device wrote first. The row's version no longer matches the one this
            // request read, so nothing was overwritten — which is the entire point.
            throw new ConflictException(
                "This record was changed on another device. Reload it and try again.")
            {
                Source = exception.Source,
            };
        }

        RefreshAggregates();
    }

    /// <summary>
    /// Runs several writes as one. Creating a cycle is a cycle row, a program, six weeks,
    /// eighteen workouts and every planned set inside them — half of that is a lifter with no
    /// plan they can train.
    /// </summary>
    public async Task<T> ExecuteInTransactionAsync<T>(
        Func<CancellationToken, Task<T>> operation,
        CancellationToken cancellationToken)
    {
        // Through the execution strategy, so a retry on a transient connection failure replays
        // the whole transaction rather than half of it.
        var strategy = context.Database.CreateExecutionStrategy();

        return await strategy.ExecuteAsync(
            cancellationToken,
            async (token) =>
            {
                await using var transaction = await context.Database.BeginTransactionAsync(token);

                var result = await operation(token);

                await transaction.CommitAsync(token);

                return result;
            });
    }

    /// <summary>
    /// Copies every aggregate this request touched back onto its rows before EF works out the
    /// SQL. One place, so no use case has to remember to announce that it changed something.
    /// </summary>
    private void FlushAggregates()
    {
        foreach (var repository in repositories)
        {
            repository.Flush();
        }
    }

    /// <summary>
    /// The interceptor stamps the new revision and sync cursor onto the rows; this hands them
    /// back to the aggregates, so what a use case returns matches what was written.
    /// </summary>
    private void RefreshAggregates()
    {
        foreach (var repository in repositories)
        {
            repository.RefreshAfterSave();
        }
    }
}
