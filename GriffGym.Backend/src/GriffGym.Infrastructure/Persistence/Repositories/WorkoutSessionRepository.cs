using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Common;
using GriffGym.Domain.Training;
using GriffGym.Domain.Workouts;
using GriffGym.Infrastructure.Persistence.Entities;
using GriffGym.Infrastructure.Persistence.Mappers;
using Microsoft.EntityFrameworkCore;

namespace GriffGym.Infrastructure.Persistence.Repositories;

internal sealed class WorkoutSessionRepository(GriffGymDbContext context)
    : TrackedRepository<WorkoutSession, WorkoutSessionRecord>, IWorkoutSessionRepository
{
    protected override void Apply(WorkoutSession domain, WorkoutSessionRecord record) =>
        WorkoutSessionMapper.Apply(domain, record);

    private IQueryable<WorkoutSessionRecord> WithLogs() =>
        context.Set<WorkoutSessionRecord>()
            .Include(session => session.Exercises)
            .ThenInclude(exercise => exercise.Sets)
            .AsSplitQuery();

    public async Task<WorkoutSession?> FindForUserAsync(
        Guid userId,
        Guid sessionId,
        CancellationToken cancellationToken)
    {
        if (Cached(sessionId) is { } cached)
        {
            return cached.UserId == userId ? cached : null;
        }

        var record = await WithLogs()
            .FirstOrDefaultAsync(
                session => session.Id == sessionId
                           && session.UserId == userId
                           && session.DeletedAtUtc == null,
                cancellationToken);

        return record is null ? null : Materialise(record, record.Id, WorkoutSessionMapper.ToDomain);
    }

    public async Task<WorkoutSession?> FindActiveForUserAsync(
        Guid userId,
        CancellationToken cancellationToken)
    {
        var record = await WithLogs()
            .Where(session =>
                session.UserId == userId
                && session.Status == WorkoutSessionStatus.InProgress
                && session.DeletedAtUtc == null)
            .OrderByDescending(session => session.StartedAtUtc)
            .FirstOrDefaultAsync(cancellationToken);

        return record is null ? null : Materialise(record, record.Id, WorkoutSessionMapper.ToDomain);
    }

    public async Task<PagedResult<WorkoutSession>> ListForUserAsync(
        Guid userId,
        WorkoutHistoryFilter filter,
        PageRequest page,
        CancellationToken cancellationToken)
    {
        var query = context.Set<WorkoutSessionRecord>()
            .Where(session => session.UserId == userId && session.DeletedAtUtc == null);

        if (filter.TrainingCycleId is { } cycleId)
        {
            query = query.Where(session => session.TrainingCycleId == cycleId);
        }

        if (filter.Status is { } status)
        {
            query = query.Where(session => session.Status == status);
        }

        if (filter.From is { } from)
        {
            query = query.Where(session => session.PerformedOn >= from);
        }

        if (filter.To is { } to)
        {
            query = query.Where(session => session.PerformedOn <= to);
        }

        var total = await query.LongCountAsync(cancellationToken);

        // The count is taken before paging, and the page itself carries the logged sets so a
        // caller that wants detail does not have to fetch each session again.
        var records = await query
            .OrderByDescending(session => session.PerformedOn)
            .ThenByDescending(session => session.StartedAtUtc)
            .Skip(page.Skip)
            .Take(page.PageSize)
            .Include(session => session.Exercises)
            .ThenInclude(exercise => exercise.Sets)
            .AsSplitQuery()
            .ToListAsync(cancellationToken);

        return new PagedResult<WorkoutSession>(
            [.. records.Select(record =>
                Materialise(record, record.Id, WorkoutSessionMapper.ToDomain))],
            page.Page,
            page.PageSize,
            total);
    }

    public async Task<IReadOnlyList<WorkoutSession>> ListAllForUserAsync(
        Guid userId,
        CancellationToken cancellationToken)
    {
        var records = await WithLogs()
            .Where(session => session.UserId == userId && session.DeletedAtUtc == null)
            .OrderBy(session => session.StartedAtUtc)
            .ToListAsync(cancellationToken);

        return [.. records.Select(record =>
            Materialise(record, record.Id, WorkoutSessionMapper.ToDomain))];
    }

    public Task<bool> ExistsAsync(Guid sessionId, CancellationToken cancellationToken) =>
        context.Set<WorkoutSessionRecord>()
            .AnyAsync(session => session.Id == sessionId, cancellationToken);

    public async Task<IReadOnlyList<CompletedWorkoutCount>> CountCompletedByWeekAsync(
        Guid userId,
        CancellationToken cancellationToken)
    {
        // Grouped in the database. Materialising three years of sessions to count them would be
        // absurd, and the numbers are only ever used as counts.
        var rows = await context.Set<WorkoutSessionRecord>()
            .Where(session =>
                session.UserId == userId
                && session.DeletedAtUtc == null
                && session.TrainingCycleId != null
                && session.Status == WorkoutSessionStatus.Completed)
            .GroupBy(session => new { session.TrainingCycleId, session.WeekNumber })
            .Select(group => new
            {
                group.Key.TrainingCycleId,
                group.Key.WeekNumber,
                Count = group.Count(),
            })
            .ToListAsync(cancellationToken);

        return
        [
            .. rows.Select(row => new CompletedWorkoutCount(
                row.TrainingCycleId!.Value,
                row.WeekNumber,
                row.Count))
        ];
    }

    public void Add(WorkoutSession session)
    {
        var record = WorkoutSessionMapper.ToRecord(session);
        context.Set<WorkoutSessionRecord>().Add(record);
        Track(session, record);
    }
}
