using GriffGym.Domain.Training;

namespace GriffGym.Domain.Tests;

/// <summary>
/// A miniature but structurally honest Griff Gym block: six weeks, three days each, week six a
/// deload. Small enough to read in a failure message, shaped like the real thing.
/// </summary>
internal static class TrainingFixtures
{
    public static readonly DateTimeOffset Now = new(2026, 3, 2, 18, 0, 0, TimeSpan.Zero);

    public static readonly Guid SquatId = Guid.Parse("00000000-0000-0000-0000-0000000000a1");
    public static readonly Guid BenchId = Guid.Parse("00000000-0000-0000-0000-0000000000a2");
    public static readonly Guid DeadliftId = Guid.Parse("00000000-0000-0000-0000-0000000000a3");

    public static ReferenceMaxSnapshot Snapshot() => ReferenceMaxSnapshot.Of(
        Weight.Of(210m),
        Weight.Of(170m),
        Weight.Of(225m));

    public static TrainingProgram Program(int weeks = 6, Guid? currentWorkoutTemplateId = null)
    {
        var sequence = 1;
        var built = new List<TrainingWeek>();

        for (var weekNumber = 1; weekNumber <= weeks; weekNumber++)
        {
            var isDeload = weekNumber == 6;

            built.Add(new TrainingWeek(
                Guid.NewGuid(),
                weekNumber,
                isDeload ? "DELOAD" : "ACCUMULATION",
                isDeload ? TrainingWeekType.Deload : TrainingWeekType.Training,
                [.. Enumerable.Range(1, 3).Select(day => Workout(weekNumber, day, sequence++, isDeload))]));
        }

        return new TrainingProgram(Guid.NewGuid(), "Blok IV — Siła", built, currentWorkoutTemplateId);
    }

    public static WorkoutTemplate Workout(int weekNumber, int dayNumber, int sequence, bool isDeload) =>
        new(
            Guid.NewGuid(),
            dayNumber,
            sequence,
            $"Week {weekNumber} Day {dayNumber}",
            [
                new ExerciseTemplate(
                    Guid.NewGuid(),
                    1,
                    SquatId,
                    "Przysiad",
                    ExerciseCategory.Squat,
                    isDeload ? ExerciseType.Deload : ExerciseType.Top,
                    [
                        new PlannedSet(Guid.NewGuid(), 1, Weight.Of(187.5m), 3, RpeTarget.Exact(8m)),
                        new PlannedSet(Guid.NewGuid(), 2, Weight.Of(175m), 3, RpeTarget.Exact(7m)),
                    ]),
            ]);

    public static TrainingCycle Cycle(
        Guid? userId = null,
        int cycleNumber = 1,
        TrainingProgram? program = null)
    {
        var plan = program ?? Program();

        return TrainingCycle.Start(
            Guid.NewGuid(),
            userId ?? Guid.NewGuid(),
            cycleNumber,
            Snapshot(),
            plan,
            Now,
            Now);
    }
}
