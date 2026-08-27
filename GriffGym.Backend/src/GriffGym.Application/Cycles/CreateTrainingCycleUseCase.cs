using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Common;
using GriffGym.Application.Exercises;
using GriffGym.Domain.Training;
using Microsoft.Extensions.Logging;

namespace GriffGym.Application.Cycles;

/// <summary>
/// Starts a cycle: the cycle row, the exercises its plan refers to, the program, its weeks,
/// their workouts and every prescribed set — one transaction or none of it.
///
/// The phone may have generated all of this offline and be uploading it after the fact, so
/// every identifier in the payload is accepted as given. Sending the same request twice returns
/// the cycle that already exists rather than creating a second one: a retry after a timeout is
/// the normal case, not an error.
/// </summary>
public sealed class CreateTrainingCycleUseCase(
    ITrainingCycleRepository cycles,
    IUnitOfWork unitOfWork,
    SynchroniseExercisesUseCase synchroniseExercises,
    ICurrentUser currentUser,
    IIdentifierFactory identifiers,
    IClock clock,
    ILogger<CreateTrainingCycleUseCase> logger)
{
    public sealed record Result(TrainingCycleView Cycle, bool WasCreated);

    public async Task<Result> ExecuteAsync(
        CreateTrainingCycleCommand command,
        CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();
        var cycleId = command.Id ?? identifiers.NewId();

        var alreadyStored = await cycles.FindForUserAsync(userId, cycleId, cancellationToken);
        if (alreadyStored is not null)
        {
            return new Result(CycleMapper.ToView(alreadyStored), WasCreated: false);
        }

        if (await cycles.ExistsAsync(cycleId, cancellationToken))
        {
            // The id is taken by somebody else's cycle. Saying so is the least informative
            // truthful answer available; it deliberately does not reveal whose.
            throw new ConflictException($"Cycle '{cycleId}' already exists.");
        }

        if (await cycles.CycleNumberExistsAsync(userId, command.CycleNumber, cancellationToken))
        {
            throw new ConflictException(
                $"Cycle {command.CycleNumber} already exists. Cycles are numbered once and never renumbered.");
        }

        return await unitOfWork.ExecuteInTransactionAsync(
            async token =>
            {
                var catalogue = await synchroniseExercises.ExecuteAsync(
                    userId,
                    command.Exercises,
                    token);

                var cycle = BuildCycle(userId, cycleId, command, catalogue);
                cycles.Add(cycle);

                await unitOfWork.SaveChangesAsync(token);

                logger.LogInformation(
                    "Cycle created {CycleId} number {CycleNumber} for {UserId}",
                    cycle.Id,
                    cycle.CycleNumber,
                    userId);

                return new Result(CycleMapper.ToView(cycle), WasCreated: true);
            },
            cancellationToken);
    }

    private TrainingCycle BuildCycle(
        Guid userId,
        Guid cycleId,
        CreateTrainingCycleCommand command,
        IReadOnlyList<Exercise> catalogue)
    {
        var now = clock.UtcNow;
        var byId = catalogue.ToDictionary(exercise => exercise.Id);

        var snapshot = ReferenceMaxSnapshot.Of(
            Weight.Of(command.SquatReferenceMaxKg),
            Weight.Of(command.BenchPressReferenceMaxKg),
            Weight.Of(command.DeadliftReferenceMaxKg));

        var weeks = command.Program.Weeks
            .Select(week => new TrainingWeek(
                week.Id ?? identifiers.NewId(),
                week.WeekNumber,
                week.Label,
                week.Type,
                [.. week.Workouts.Select(workout => BuildWorkout(workout, byId))]))
            .ToList();

        var program = new TrainingProgram(
            command.Program.Id ?? identifiers.NewId(),
            command.Program.Name,
            weeks,
            command.Program.CurrentWorkoutTemplateId ?? FirstWorkoutOf(weeks));

        return TrainingCycle.Start(
            cycleId,
            userId,
            command.CycleNumber,
            snapshot,
            program,
            command.StartedAtUtc,
            now);
    }

    private WorkoutTemplate BuildWorkout(
        WorkoutTemplateInput input,
        IReadOnlyDictionary<Guid, Exercise> catalogue) =>
        new(
            input.Id ?? identifiers.NewId(),
            input.DayNumber,
            input.SequenceNumber,
            input.Title,
            [.. input.Exercises.Select(exercise => BuildExercise(exercise, catalogue))]);

    private ExerciseTemplate BuildExercise(
        ExerciseTemplateInput input,
        IReadOnlyDictionary<Guid, Exercise> catalogue)
    {
        if (!catalogue.TryGetValue(input.ExerciseId, out var exercise))
        {
            throw new NotFoundException("Exercise", input.ExerciseId);
        }

        return new ExerciseTemplate(
            input.Id ?? identifiers.NewId(),
            input.Position,
            exercise.Id,
            input.ExerciseName ?? exercise.Name,
            input.ExerciseCategory ?? exercise.Category,
            input.Type,
            [.. input.PlannedSets.Select(BuildPlannedSet)]);
    }

    private PlannedSet BuildPlannedSet(PlannedSetInput input) => new(
        input.Id ?? identifiers.NewId(),
        input.Position,
        Weight.OfNullable(input.WeightKg),
        input.Reps,
        RpeTarget.FromBounds(input.RpeMin, input.RpeMax));

    /// <summary>
    /// A brand new cycle starts pointing at its first unit. The plan is a sequence, so "where
    /// am I?" has an answer from the moment the cycle exists.
    /// </summary>
    private static Guid? FirstWorkoutOf(IReadOnlyList<TrainingWeek> weeks) =>
        weeks.OrderBy(week => week.WeekNumber)
            .SelectMany(week => week.Workouts.OrderBy(workout => workout.DayNumber))
            .Select(workout => (Guid?)workout.Id)
            .FirstOrDefault();
}
