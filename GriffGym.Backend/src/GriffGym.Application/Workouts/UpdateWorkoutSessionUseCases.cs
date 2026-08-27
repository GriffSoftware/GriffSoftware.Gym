using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Common;
using GriffGym.Domain.Training;
using GriffGym.Domain.Workouts;
using Microsoft.Extensions.Logging;

namespace GriffGym.Application.Workouts;

/// <summary>
/// Guards every write against the revision the client thought it was writing over.
///
/// A full conflict resolver is a later phase; detecting the conflict is not. Without this, two
/// phones open on the same workout silently lose one lifter's sets, and there is no worse
/// failure mode for a training log.
/// </summary>
internal static class VersionGuard
{
    public static void Check(WorkoutSession session, int? expectedVersion)
    {
        if (expectedVersion is { } expected && expected != session.Version)
        {
            throw new ConcurrencyConflictException("Workout", expected, session.Version);
        }
    }
}

/// <summary>
/// Replaces the mutable part of a live session with what the client holds.
///
/// Sending the tree wholesale is idempotent and immune to a lost intermediate request, which
/// matters more than bandwidth for a phone that has been offline in a basement gym.
/// </summary>
public sealed class UpdateWorkoutSessionUseCase(
    IWorkoutSessionRepository sessions,
    IUnitOfWork unitOfWork,
    ICurrentUser currentUser,
    IIdentifierFactory identifiers,
    IClock clock)
{
    private readonly WorkoutLogFactory _logs = new(identifiers);

    public async Task<WorkoutSessionView> ExecuteAsync(
        UpdateWorkoutSessionCommand command,
        CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();
        var now = clock.UtcNow;

        var session = await sessions.FindForUserAsync(userId, command.Id, cancellationToken)
                      ?? throw new NotFoundException("Workout", command.Id);

        VersionGuard.Check(session, command.ExpectedVersion);

        if (command.Exercises is not null)
        {
            session.ReplaceExercises(_logs.Build(command.Exercises), now);
        }

        if (command.Notes is not null)
        {
            session.UpdateNotes(command.Notes, now);
        }

        await unitOfWork.SaveChangesAsync(cancellationToken);

        return WorkoutMapper.ToView(session);
    }
}

/// <summary>
/// Writes one set the moment the lifter finishes it.
///
/// This is the hot path of the whole product: every keystroke that parses goes straight
/// through, so a session survives the app being killed between sets.
/// </summary>
public sealed class LogSetUseCase(
    IWorkoutSessionRepository sessions,
    IUnitOfWork unitOfWork,
    ICurrentUser currentUser,
    IClock clock)
{
    public async Task<WorkoutSessionView> ExecuteAsync(
        LogSetCommand command,
        CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();

        var session = await sessions.FindForUserAsync(userId, command.SessionId, cancellationToken)
                      ?? throw new NotFoundException("Workout", command.SessionId);

        VersionGuard.Check(session, command.ExpectedVersion);

        session.LogSet(
            command.SetLogId,
            new SetResult(
                Weight.OfNullable(command.WeightKg),
                command.Reps,
                Rpe.OfNullable(command.Rpe),
                command.Completed,
                command.Notes),
            clock.UtcNow);

        await unitOfWork.SaveChangesAsync(cancellationToken);

        return WorkoutMapper.ToView(session);
    }
}

public sealed class CompleteWorkoutSessionUseCase(
    IWorkoutSessionRepository sessions,
    IUnitOfWork unitOfWork,
    ICurrentUser currentUser,
    IClock clock,
    ILogger<CompleteWorkoutSessionUseCase> logger)
{
    public async Task<WorkoutSessionView> ExecuteAsync(
        FinishWorkoutSessionCommand command,
        CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();
        var now = clock.UtcNow;

        var session = await sessions.FindForUserAsync(userId, command.Id, cancellationToken)
                      ?? throw new NotFoundException("Workout", command.Id);

        VersionGuard.Check(session, command.ExpectedVersion);

        session.Complete(command.FinishedAtUtc ?? now, now);
        await unitOfWork.SaveChangesAsync(cancellationToken);

        logger.LogInformation(
            "Workout completed {SessionId} volume {VolumeKg} kg for {UserId}",
            session.Id,
            session.TotalVolume.Kilograms,
            userId);

        return WorkoutMapper.ToView(session);
    }
}

public sealed class CancelWorkoutSessionUseCase(
    IWorkoutSessionRepository sessions,
    IUnitOfWork unitOfWork,
    ICurrentUser currentUser,
    IClock clock)
{
    public async Task<WorkoutSessionView> ExecuteAsync(
        FinishWorkoutSessionCommand command,
        CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();
        var now = clock.UtcNow;

        var session = await sessions.FindForUserAsync(userId, command.Id, cancellationToken)
                      ?? throw new NotFoundException("Workout", command.Id);

        VersionGuard.Check(session, command.ExpectedVersion);

        session.Cancel(command.FinishedAtUtc ?? now, now);
        await unitOfWork.SaveChangesAsync(cancellationToken);

        return WorkoutMapper.ToView(session);
    }
}
