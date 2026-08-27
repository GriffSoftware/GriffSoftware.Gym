using GriffGym.Application.Cycles;
using GriffGym.Application.Exercises;
using GriffGym.Application.ReferenceMaxes;
using GriffGym.Application.State;
using GriffGym.Application.Users;
using GriffGym.Application.Workouts;
using GriffGym.Domain.Training;
using Microsoft.Extensions.Logging.Abstractions;

namespace GriffGym.Application.Tests;

/// <summary>The training use cases, wired over the in-memory doubles.</summary>
internal sealed class TrainingHarness
{
    public static readonly DateTimeOffset Start = new(2026, 3, 2, 18, 0, 0, TimeSpan.Zero);

    public static readonly Guid SquatExerciseId = Guid.Parse("aaaaaaaa-0000-0000-0000-000000000001");
    public static readonly Guid BenchExerciseId = Guid.Parse("aaaaaaaa-0000-0000-0000-000000000002");

    public Guid UserId { get; } = Guid.Parse("11111111-1111-1111-1111-111111111111");

    public FakeClock Clock { get; } = new(Start);

    public FakeIdentifierFactory Identifiers { get; } = new();

    public FakeUnitOfWork UnitOfWork { get; } = new();

    public FakeUserRepository Users { get; } = new();

    public FakeExerciseRepository Exercises { get; } = new();

    public FakeReferenceMaxRepository ReferenceMaxes { get; } = new();

    public FakeTrainingCycleRepository Cycles { get; } = new();

    public FakeWorkoutSessionRepository Sessions { get; } = new();

    public FakeCurrentUser CurrentUser { get; }

    public TrainingHarness()
    {
        CurrentUser = new FakeCurrentUser(UserId);

        Users.Add(GriffGym.Domain.Users.User.Register(
            UserId,
            GriffGym.Domain.Users.EmailAddress.Of("lifter@example.com"),
            "hashed:pw",
            Start));
    }

    public SynchroniseExercisesUseCase SynchroniseExercises => new(Exercises, Identifiers, Clock);

    public CreateTrainingCycleUseCase CreateCycle => new(
        Cycles, UnitOfWork, SynchroniseExercises, CurrentUser, Identifiers, Clock,
        NullLogger<CreateTrainingCycleUseCase>.Instance);

    public GetTrainingCyclesUseCase GetCycles => new(Cycles, Sessions, CurrentUser);

    public GetTrainingCycleUseCase GetCycle => new(Cycles, CurrentUser);

    public CompleteTrainingCycleUseCase CompleteCycle => new(
        Cycles, UnitOfWork, CurrentUser, Clock, NullLogger<CompleteTrainingCycleUseCase>.Instance);

    public UpdateCycleProgressUseCase UpdateProgress => new(Cycles, UnitOfWork, CurrentUser, Clock);

    public UpdateReferenceMaxUseCase UpdateReferenceMax => new(
        ReferenceMaxes, UnitOfWork, CurrentUser, Identifiers, Clock,
        NullLogger<UpdateReferenceMaxUseCase>.Instance);

    public GetReferenceMaxesUseCase GetReferenceMaxes => new(ReferenceMaxes, CurrentUser);

    public CreateWorkoutSessionUseCase CreateWorkout => new(
        Sessions, Cycles, UnitOfWork, CurrentUser, Identifiers, Clock,
        NullLogger<CreateWorkoutSessionUseCase>.Instance);

    public LogSetUseCase LogSet => new(Sessions, UnitOfWork, CurrentUser, Clock);

    public UpdateWorkoutSessionUseCase UpdateWorkout => new(
        Sessions, UnitOfWork, CurrentUser, Identifiers, Clock);

    public CompleteWorkoutSessionUseCase CompleteWorkout => new(
        Sessions, UnitOfWork, CurrentUser, Clock,
        NullLogger<CompleteWorkoutSessionUseCase>.Instance);

    public GetActiveWorkoutUseCase GetActiveWorkout => new(Sessions, CurrentUser);

    public GetWorkoutHistoryUseCase GetHistory => new(Sessions, CurrentUser);

    public GetUserApplicationStateUseCase GetState => new(
        Users, ReferenceMaxes, Exercises, Cycles, Sessions, CurrentUser, Clock,
        NullLogger<GetUserApplicationStateUseCase>.Instance);

    /// <summary>
    /// A six-week block shaped like the real one: weeks one to five train, week six deloads at
    /// half the reference max, three days each.
    /// </summary>
    public static CreateTrainingCycleCommand SixWeekBlock(Guid? id = null, int cycleNumber = 1)
    {
        const decimal squatMax = 210m;
        var sequence = 1;
        var weeks = new List<TrainingWeekInput>();

        for (var weekNumber = 1; weekNumber <= 6; weekNumber++)
        {
            var isDeload = weekNumber == 6;
            var workouts = new List<WorkoutTemplateInput>();

            for (var dayNumber = 1; dayNumber <= 3; dayNumber++)
            {
                var percent = isDeload ? 50m : 80m + weekNumber;

                workouts.Add(new WorkoutTemplateInput(
                    Deterministic(weekNumber, dayNumber, 0),
                    dayNumber,
                    sequence++,
                    $"Week {weekNumber} Day {dayNumber}",
                    [
                        new ExerciseTemplateInput(
                            Deterministic(weekNumber, dayNumber, 1),
                            1,
                            SquatExerciseId,
                            null,
                            null,
                            isDeload ? ExerciseType.Deload : ExerciseType.Top,
                            [
                                new PlannedSetInput(
                                    Deterministic(weekNumber, dayNumber, 2),
                                    1,
                                    decimal.Round(squatMax * percent / 100m, 2),
                                    3,
                                    8m,
                                    8m),
                                new PlannedSetInput(
                                    Deterministic(weekNumber, dayNumber, 3),
                                    2,
                                    decimal.Round(squatMax * (percent - 6m) / 100m, 2),
                                    3,
                                    7m,
                                    7m),
                            ]),
                    ]));
            }

            weeks.Add(new TrainingWeekInput(
                Deterministic(weekNumber, 0, 9),
                weekNumber,
                isDeload ? "DELOAD" : "ACCUMULATION",
                isDeload ? TrainingWeekType.Deload : TrainingWeekType.Training,
                workouts));
        }

        return new CreateTrainingCycleCommand(
            id,
            cycleNumber,
            squatMax,
            170m,
            225m,
            Start,
            [
                new ExerciseInput(SquatExerciseId, "Przysiad", ExerciseCategory.Squat),
                new ExerciseInput(BenchExerciseId, "Ławka", ExerciseCategory.BenchPress),
            ],
            new TrainingProgramInput(
                Deterministic(0, 0, 7),
                "Blok IV — Siła",
                null,
                weeks));
    }

    private static Guid Deterministic(int week, int day, int kind) =>
        new(0x5000 + kind, (short)week, (short)day, [0, 0, 0, 0, 0, 0, 0, 1]);
}
