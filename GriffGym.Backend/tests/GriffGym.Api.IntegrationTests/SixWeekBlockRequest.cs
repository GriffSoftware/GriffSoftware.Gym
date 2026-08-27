using GriffGym.Api.Contracts.V1;
using GriffGym.Domain.Training;

namespace GriffGym.Api.IntegrationTests;

/// <summary>
/// The payload a phone sends when it starts a cycle: the whole Griff Gym block, generated
/// locally, uploaded in one request.
///
/// Six weeks, three days each, weeks one to five training and week six a deload at half the
/// reference max — the shape the app actually produces, so the tests exercise the tree depth
/// and the half-kilogram loads that real data has.
/// </summary>
public static class SixWeekBlockRequest
{
    public const decimal SquatMax = 210m;
    public const decimal BenchMax = 170m;
    public const decimal DeadliftMax = 225m;

    public static readonly DateTimeOffset StartedAt = new(2026, 3, 2, 18, 0, 0, TimeSpan.Zero);

    public static CreateCycleRequest Build(
        Guid? cycleId = null,
        int cycleNumber = 1,
        Guid? squatExerciseId = null)
    {
        var squatId = squatExerciseId ?? Guid.NewGuid();
        var benchId = Guid.NewGuid();
        var sequence = 1;
        var weeks = new List<WeekRequest>();

        for (var weekNumber = 1; weekNumber <= 6; weekNumber++)
        {
            var isDeload = weekNumber == 6;
            var workouts = new List<WorkoutTemplateRequest>();

            for (var dayNumber = 1; dayNumber <= 3; dayNumber++)
            {
                var top = isDeload ? 105m : 187.5m;
                var backOff = isDeload ? 102.5m : 162.5m;

                workouts.Add(new WorkoutTemplateRequest(
                    Guid.NewGuid(),
                    dayNumber,
                    sequence++,
                    $"Week {weekNumber} Day {dayNumber}",
                    [
                        new ExerciseTemplateRequest(
                            Guid.NewGuid(),
                            1,
                            squatId,
                            null,
                            null,
                            isDeload ? ExerciseType.Deload : ExerciseType.Top,
                            [
                                new PlannedSetRequest(Guid.NewGuid(), 1, top, 3, 8m, 8m),
                                new PlannedSetRequest(Guid.NewGuid(), 2, backOff, 3, 6m, 7m),
                            ]),
                        new ExerciseTemplateRequest(
                            Guid.NewGuid(),
                            2,
                            benchId,
                            null,
                            null,
                            isDeload ? ExerciseType.Deload : ExerciseType.Volume,
                            [
                                new PlannedSetRequest(Guid.NewGuid(), 1, 125m, 6, 6m, 7m),
                            ]),
                    ]));
            }

            weeks.Add(new WeekRequest(
                Guid.NewGuid(),
                weekNumber,
                isDeload ? "DELOAD" : "ACCUMULATION",
                isDeload ? TrainingWeekType.Deload : TrainingWeekType.Training,
                workouts));
        }

        return new CreateCycleRequest(
            cycleId,
            cycleNumber,
            SquatMax,
            BenchMax,
            DeadliftMax,
            StartedAt,
            [
                new ExerciseRequest(squatId, "Przysiad", ExerciseCategory.Squat),
                new ExerciseRequest(benchId, "Ławka", ExerciseCategory.BenchPress),
            ],
            new ProgramRequest(Guid.NewGuid(), "Blok IV — Siła", null, weeks));
    }
}
