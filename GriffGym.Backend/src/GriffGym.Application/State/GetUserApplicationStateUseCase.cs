using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Common;
using GriffGym.Application.Cycles;
using GriffGym.Application.Exercises;
using GriffGym.Application.ReferenceMaxes;
using GriffGym.Application.Users;
using GriffGym.Application.Workouts;
using GriffGym.Domain.Common;
using GriffGym.Domain.Training;
using Microsoft.Extensions.Logging;

namespace GriffGym.Application.State;

/// <summary>
/// Everything one lifter's installation is made of, in one read-only document.
///
/// This is the answer to "my phone is in a river". A fresh install signs in, asks once, and has
/// enough to rebuild its local database exactly: the planning numbers, every cycle with the
/// full plan it was trained on, where the lifter is inside the current plan, all logged
/// sessions with their planned and actual sets, and the workout that is still open.
///
/// <see cref="SchemaVersion"/> is the shape of this document, not the shape of the database. A
/// client can refuse a version it does not understand instead of restoring nonsense.
/// </summary>
public sealed record UserApplicationState(
    int SchemaVersion,
    DateTimeOffset GeneratedAtUtc,
    long SyncVersion,
    UserProfile Profile,
    IReadOnlyList<ReferenceMaxView> ReferenceMaxes,
    IReadOnlyList<ExerciseView> Exercises,
    IReadOnlyList<TrainingCycleView> Cycles,
    Guid? CurrentCycleId,
    Guid? ActiveWorkoutId,
    IReadOnlyList<WorkoutSessionView> Workouts);

public sealed class GetUserApplicationStateUseCase(
    IUserRepository users,
    IReferenceMaxRepository referenceMaxes,
    IExerciseRepository exercises,
    ITrainingCycleRepository cycles,
    IWorkoutSessionRepository sessions,
    ICurrentUser currentUser,
    IClock clock,
    ILogger<GetUserApplicationStateUseCase> logger)
{
    /// <summary>Bumped whenever this document's shape changes in a way clients must notice.</summary>
    public const int CurrentSchemaVersion = 1;

    public async Task<UserApplicationState> ExecuteAsync(CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();

        var user = await users.FindByIdAsync(userId, cancellationToken)
                   ?? throw new NotFoundException("User", userId);

        // Deliberately a handful of scoped reads rather than one enormous Include: each of
        // these is a query the repository already knows how to run efficiently, and composing
        // them here keeps the shape of the document a decision of the application layer.
        var storedMaxes = await referenceMaxes.ListForUserAsync(userId, cancellationToken);
        var storedExercises = await exercises.ListForUserAsync(userId, cancellationToken);
        var storedCycles = await cycles.ListForUserAsync(userId, cancellationToken);
        var storedSessions = await sessions.ListAllForUserAsync(userId, cancellationToken);

        var cycleViews = storedCycles
            .OrderBy(cycle => cycle.CycleNumber)
            .Select(CycleMapper.ToView)
            .ToList();

        var workoutViews = storedSessions
            .OrderBy(session => session.StartedAtUtc)
            .Select(WorkoutMapper.ToView)
            .ToList();

        var state = new UserApplicationState(
            CurrentSchemaVersion,
            clock.UtcNow,
            HighestSyncVersion([user], storedMaxes, storedExercises, storedCycles, storedSessions),
            new UserProfile(user.Id, user.Email.Value, user.CreatedAtUtc, user.UpdatedAtUtc),
            [.. storedMaxes.OrderBy(max => max.Lift).Select(ReferenceMaxMapper.ToView)],
            [.. storedExercises.OrderBy(exercise => exercise.Name, StringComparer.Ordinal)
                .Select(ExerciseMapper.ToView)],
            cycleViews,
            storedCycles.Count == 0
                ? null
                : storedCycles.OrderByDescending(cycle => cycle.CycleNumber).First().Id,
            storedSessions
                .Where(session => session.Status == WorkoutSessionStatus.InProgress)
                .Select(session => (Guid?)session.Id)
                .FirstOrDefault(),
            workoutViews);

        logger.LogInformation(
            "State restored for {UserId}: {Cycles} cycles, {Workouts} workouts, sync version {SyncVersion}",
            userId,
            cycleViews.Count,
            workoutViews.Count,
            state.SyncVersion);

        return state;
    }

    /// <summary>
    /// The cursor a future delta sync will page from: "everything that changed above this".
    /// Taken across every syncable record so that nothing the document contains sits above it.
    /// </summary>
    private static long HighestSyncVersion(params IEnumerable<Entity>[] groups) =>
        groups.SelectMany(group => group)
            .Select(entity => entity.SyncVersion)
            .DefaultIfEmpty(0L)
            .Max();
}
