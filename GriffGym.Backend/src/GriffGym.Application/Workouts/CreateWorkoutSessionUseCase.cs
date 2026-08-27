using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Common;
using GriffGym.Domain.Common;
using GriffGym.Domain.Training;
using GriffGym.Domain.Workouts;
using Microsoft.Extensions.Logging;

namespace GriffGym.Application.Workouts;

public sealed class CreateWorkoutSessionUseCase(
    IWorkoutSessionRepository sessions,
    ITrainingCycleRepository cycles,
    IUnitOfWork unitOfWork,
    ICurrentUser currentUser,
    IIdentifierFactory identifiers,
    IClock clock,
    ILogger<CreateWorkoutSessionUseCase> logger)
{
    private readonly WorkoutLogFactory _logs = new(identifiers);

    public sealed record Result(WorkoutSessionView Session, bool WasCreated);

    public async Task<Result> ExecuteAsync(
        CreateWorkoutSessionCommand command,
        CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();
        var sessionId = command.Id ?? identifiers.NewId();

        // A phone that retried after a timeout gets back the session it already created rather
        // than a second copy of the same workout.
        var alreadyStored = await sessions.FindForUserAsync(userId, sessionId, cancellationToken);
        if (alreadyStored is not null)
        {
            return new Result(WorkoutMapper.ToView(alreadyStored), WasCreated: false);
        }

        if (await sessions.ExistsAsync(sessionId, cancellationToken))
        {
            throw new ConflictException($"Workout '{sessionId}' already exists.");
        }

        if (command.Status == WorkoutSessionStatus.InProgress)
        {
            var active = await sessions.FindActiveForUserAsync(userId, cancellationToken);
            if (active is not null)
            {
                throw new ConflictException(
                    $"'{active.Title}' is still in progress. Finish or cancel it before starting another workout.");
            }
        }

        var now = clock.UtcNow;
        var session = command.Exercises is { Count: > 0 }
            ? BuildFromPayload(userId, sessionId, command, now)
            : await BuildFromTemplateAsync(userId, sessionId, command, now, cancellationToken);

        sessions.Add(session);
        await unitOfWork.SaveChangesAsync(cancellationToken);

        logger.LogInformation(
            "Workout created {SessionId} status {Status} for {UserId}",
            session.Id,
            session.Status,
            userId);

        return new Result(WorkoutMapper.ToView(session), WasCreated: true);
    }

    /// <summary>The ordinary path: snapshot the planned unit the lifter pressed START on.</summary>
    private async Task<WorkoutSession> BuildFromTemplateAsync(
        Guid userId,
        Guid sessionId,
        CreateWorkoutSessionCommand command,
        DateTimeOffset now,
        CancellationToken cancellationToken)
    {
        if (command.WorkoutTemplateId is not { } templateId || command.TrainingCycleId is not { } cycleId)
        {
            throw new DomainException(
                "A workout needs either a training cycle and workout template to snapshot, or its own exercises.");
        }

        var cycle = await cycles.FindForUserAsync(userId, cycleId, cancellationToken)
                    ?? throw new NotFoundException("Cycle", cycleId);

        var template = cycle.Program.FindWorkout(templateId)
                       ?? throw new NotFoundException("Workout template", templateId);

        var startedAt = command.StartedAtUtc ?? now;

        var session = WorkoutSession.StartFromTemplate(
            sessionId,
            userId,
            cycle,
            template,
            command.PerformedOn ?? DateOnly.FromDateTime(startedAt.UtcDateTime),
            startedAt,
            now,
            identifiers.NewId,
            identifiers.NewId);

        if (command.Notes is not null)
        {
            session.UpdateNotes(command.Notes, now);
        }

        return session;
    }

    /// <summary>
    /// The upload path: the client already has the whole session, finished or not. Used both
    /// for a workout started offline and for backfilling history after creating an account.
    /// </summary>
    private WorkoutSession BuildFromPayload(
        Guid userId,
        Guid sessionId,
        CreateWorkoutSessionCommand command,
        DateTimeOffset now)
    {
        var startedAt = command.StartedAtUtc ?? now;
        DateTimeOffset? finishedAt = command.Status.IsFinished()
            ? command.FinishedAtUtc ?? startedAt
            : null;

        return WorkoutSession.Create(
            sessionId,
            userId,
            command.TrainingCycleId,
            command.TrainingWeekId,
            command.WorkoutTemplateId,
            command.WeekNumber ?? 1,
            command.DayNumber ?? 1,
            command.Title ?? "Workout",
            command.IsDeload ?? false,
            command.Status,
            command.PerformedOn ?? DateOnly.FromDateTime(startedAt.UtcDateTime),
            startedAt,
            finishedAt,
            command.Notes,
            _logs.Build(command.Exercises ?? []),
            now);
    }
}
