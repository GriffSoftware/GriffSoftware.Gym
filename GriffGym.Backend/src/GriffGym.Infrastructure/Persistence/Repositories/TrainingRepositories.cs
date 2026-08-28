using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Domain.Training;
using GriffGym.Infrastructure.Persistence.Entities;
using GriffGym.Infrastructure.Persistence.Mappers;
using Microsoft.EntityFrameworkCore;

namespace GriffGym.Infrastructure.Persistence.Repositories;

internal sealed class ExerciseRepository(GriffGymDbContext context)
    : TrackedRepository<Exercise, ExerciseRecord>, IExerciseRepository
{
    protected override void Apply(Exercise domain, ExerciseRecord record) =>
        ExerciseMapper.Apply(domain, record);

    public async Task<IReadOnlyList<Exercise>> ListForUserAsync(
        Guid userId,
        CancellationToken cancellationToken)
    {
        var records = await context.Set<ExerciseRecord>()
            .Where(exercise => exercise.UserId == userId && exercise.DeletedAtUtc == null)
            .OrderBy(exercise => exercise.Name)
            .ToListAsync(cancellationToken);

        return [.. records.Select(record => Materialise(record, record.Id, ExerciseMapper.ToDomain))];
    }

    public async Task<IReadOnlyList<Exercise>> ListForUserAsync(
        Guid userId,
        IReadOnlyCollection<Guid> ids,
        CancellationToken cancellationToken)
    {
        if (ids.Count == 0)
        {
            return [];
        }

        var records = await context.Set<ExerciseRecord>()
            .Where(exercise =>
                exercise.UserId == userId
                && exercise.DeletedAtUtc == null
                && ids.Contains(exercise.Id))
            .ToListAsync(cancellationToken);

        return [.. records.Select(record => Materialise(record, record.Id, ExerciseMapper.ToDomain))];
    }

    public void Add(Exercise exercise)
    {
        var record = ExerciseMapper.ToRecord(exercise);
        context.Set<ExerciseRecord>().Add(record);
        Track(exercise, record);
    }

    /// <summary>
    /// Only safe once nothing prescribes these movements any more. <c>exercise_template</c>
    /// references <c>exercise</c> with <c>RESTRICT</c>, so running this before the plans are
    /// gone raises a foreign key violation rather than quietly leaving rows behind — which is
    /// the behaviour worth having, and is why account deletion removes the plans first.
    /// </summary>
    public Task<int> DeleteAllForUserAsync(Guid userId, CancellationToken cancellationToken) =>
        context.Set<ExerciseRecord>()
            .Where(exercise => exercise.UserId == userId)
            .ExecuteDeleteAsync(cancellationToken);
}

internal sealed class ReferenceMaxRepository(GriffGymDbContext context)
    : TrackedRepository<ReferenceMax, ReferenceMaxRecord>, IReferenceMaxRepository
{
    protected override void Apply(ReferenceMax domain, ReferenceMaxRecord record) =>
        ReferenceMaxMapper.Apply(domain, record);

    public async Task<IReadOnlyList<ReferenceMax>> ListForUserAsync(
        Guid userId,
        CancellationToken cancellationToken)
    {
        var records = await context.Set<ReferenceMaxRecord>()
            .Where(max => max.UserId == userId && max.DeletedAtUtc == null)
            .ToListAsync(cancellationToken);

        return [.. records.Select(record =>
            Materialise(record, record.Id, ReferenceMaxMapper.ToDomain))];
    }

    public async Task<ReferenceMax?> FindForUserAsync(
        Guid userId,
        LiftType lift,
        CancellationToken cancellationToken)
    {
        var record = await context.Set<ReferenceMaxRecord>()
            .FirstOrDefaultAsync(
                max => max.UserId == userId && max.Lift == lift && max.DeletedAtUtc == null,
                cancellationToken);

        return record is null ? null : Materialise(record, record.Id, ReferenceMaxMapper.ToDomain);
    }

    public void Add(ReferenceMax referenceMax)
    {
        var record = ReferenceMaxMapper.ToRecord(referenceMax);
        context.Set<ReferenceMaxRecord>().Add(record);
        Track(referenceMax, record);
    }

    public Task<int> DeleteAllForUserAsync(Guid userId, CancellationToken cancellationToken) =>
        context.Set<ReferenceMaxRecord>()
            .Where(referenceMax => referenceMax.UserId == userId)
            .ExecuteDeleteAsync(cancellationToken);
}

internal sealed class TrainingCycleRepository(GriffGymDbContext context)
    : TrackedRepository<TrainingCycle, TrainingCycleRecord>, ITrainingCycleRepository
{
    protected override void Apply(TrainingCycle domain, TrainingCycleRecord record) =>
        TrainingCycleMapper.Apply(domain, record);

    /// <summary>
    /// A cycle is only meaningful with its plan attached, so it is always loaded whole.
    ///
    /// Split into several statements rather than one cartesian join: six weeks times three days
    /// times several exercises times several sets multiplies out to thousands of duplicated
    /// cycle rows in a single-query plan.
    /// </summary>
    private IQueryable<TrainingCycleRecord> WithProgram() =>
        context.Set<TrainingCycleRecord>()
            .Include(cycle => cycle.Program!)
            .ThenInclude(program => program.Weeks)
            .ThenInclude(week => week.Workouts)
            .ThenInclude(workout => workout.Exercises)
            .ThenInclude(exercise => exercise.PlannedSets)
            .AsSplitQuery();

    public async Task<TrainingCycle?> FindForUserAsync(
        Guid userId,
        Guid cycleId,
        CancellationToken cancellationToken)
    {
        if (Cached(cycleId) is { } cached)
        {
            return cached.UserId == userId ? cached : null;
        }

        // Ownership is part of the query, not a check afterwards. There is no code path that
        // loads a cycle first and asks whose it is second.
        var record = await WithProgram()
            .FirstOrDefaultAsync(
                cycle => cycle.Id == cycleId && cycle.UserId == userId && cycle.DeletedAtUtc == null,
                cancellationToken);

        return record is null ? null : Materialise(record, record.Id, TrainingCycleMapper.ToDomain);
    }

    public async Task<IReadOnlyList<TrainingCycle>> ListForUserAsync(
        Guid userId,
        CancellationToken cancellationToken)
    {
        var records = await WithProgram()
            .Where(cycle => cycle.UserId == userId && cycle.DeletedAtUtc == null)
            .OrderByDescending(cycle => cycle.CycleNumber)
            .ToListAsync(cancellationToken);

        return [.. records.Select(record =>
            Materialise(record, record.Id, TrainingCycleMapper.ToDomain))];
    }

    public async Task<TrainingCycle?> FindCurrentForUserAsync(
        Guid userId,
        CancellationToken cancellationToken)
    {
        var record = await WithProgram()
            .Where(cycle => cycle.UserId == userId && cycle.DeletedAtUtc == null)
            .OrderByDescending(cycle => cycle.CycleNumber)
            .FirstOrDefaultAsync(cancellationToken);

        return record is null ? null : Materialise(record, record.Id, TrainingCycleMapper.ToDomain);
    }

    public Task<bool> CycleNumberExistsAsync(
        Guid userId,
        int cycleNumber,
        CancellationToken cancellationToken) =>
        context.Set<TrainingCycleRecord>()
            .AnyAsync(
                cycle => cycle.UserId == userId
                         && cycle.CycleNumber == cycleNumber
                         && cycle.DeletedAtUtc == null,
                cancellationToken);

    /// <summary>
    /// Across all users on purpose: identifiers are global, and creating a second row with an id
    /// somebody else already owns would be a primary key violation deep inside a transaction.
    /// </summary>
    public Task<bool> ExistsAsync(Guid cycleId, CancellationToken cancellationToken) =>
        context.Set<TrainingCycleRecord>().AnyAsync(cycle => cycle.Id == cycleId, cancellationToken);

    public void Add(TrainingCycle cycle)
    {
        var record = TrainingCycleMapper.ToRecord(cycle);
        context.Set<TrainingCycleRecord>().Add(record);
        Track(cycle, record);
    }

    /// <summary>
    /// One statement; PostgreSQL cascades it down through program, weeks, workout templates,
    /// exercise templates and planned sets. Loading a few years of plans into memory to delete
    /// them row by row would be the same outcome at a far worse price.
    /// </summary>
    public Task<int> DeleteAllForUserAsync(Guid userId, CancellationToken cancellationToken) =>
        context.Set<TrainingCycleRecord>()
            .Where(cycle => cycle.UserId == userId)
            .ExecuteDeleteAsync(cancellationToken);
}
