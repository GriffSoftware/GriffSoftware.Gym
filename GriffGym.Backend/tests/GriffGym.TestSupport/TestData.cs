using GriffGym.Domain.Training;
using GriffGym.Domain.Users;
using GriffGym.Domain.Workouts;

namespace GriffGym.TestSupport;

/// <summary>
/// A Griff Gym block built out of domain objects: six weeks, three days each, week six a
/// deload at half the reference max. Shaped like the real thing so persistence tests exercise
/// the tree depth that actually ships.
/// </summary>
public static class TestData
{
    public static readonly DateTimeOffset Now = new(2026, 3, 2, 18, 0, 0, TimeSpan.Zero);

    public const decimal SquatMax = 210m;
    public const decimal BenchMax = 170m;
    public const decimal DeadliftMax = 225m;

    public static User User(Guid id, string email = "lifter@example.com") =>
        Domain.Users.User.Register(id, EmailAddress.Of(email), "hashed:pw", Now);

    public static Exercise Squat(Guid userId, Guid? id = null) =>
        Exercise.Create(
            id ?? Guid.NewGuid(),
            userId,
            "Przysiad",
            ExerciseCategory.Squat,
            Now);

    public static TrainingCycle Cycle(
        Guid userId,
        Guid squatExerciseId,
        Guid? cycleId = null,
        int cycleNumber = 1)
    {
        var sequence = 1;
        var weeks = new List<TrainingWeek>();

        for (var weekNumber = 1; weekNumber <= 6; weekNumber++)
        {
            var isDeload = weekNumber == 6;
            var workouts = new List<WorkoutTemplate>();

            for (var dayNumber = 1; dayNumber <= 3; dayNumber++)
            {
                // Half kilograms throughout, because that is what this program prescribes and
                // what a numeric(7,2) column has to carry back unchanged.
                var top = isDeload ? Weight.Of(105m) : Weight.Of(187.5m);
                var backOff = isDeload ? Weight.Of(102.5m) : Weight.Of(162.5m);

                workouts.Add(new WorkoutTemplate(
                    Guid.NewGuid(),
                    dayNumber,
                    sequence++,
                    $"Week {weekNumber} Day {dayNumber}",
                    [
                        new ExerciseTemplate(
                            Guid.NewGuid(),
                            1,
                            squatExerciseId,
                            "Przysiad",
                            ExerciseCategory.Squat,
                            isDeload ? ExerciseType.Deload : ExerciseType.Top,
                            [
                                new PlannedSet(Guid.NewGuid(), 1, top, 3, RpeTarget.Exact(8m)),
                                new PlannedSet(
                                    Guid.NewGuid(), 2, backOff, 3, RpeTarget.Range(6m, 7m)),
                            ]),
                    ]));
            }

            weeks.Add(new TrainingWeek(
                Guid.NewGuid(),
                weekNumber,
                isDeload ? "DELOAD" : "ACCUMULATION",
                isDeload ? TrainingWeekType.Deload : TrainingWeekType.Training,
                workouts));
        }

        var program = new TrainingProgram(Guid.NewGuid(), "Blok IV — Siła", weeks, null);

        return TrainingCycle.Start(
            cycleId ?? Guid.NewGuid(),
            userId,
            cycleNumber,
            ReferenceMaxSnapshot.Of(
                Weight.Of(SquatMax),
                Weight.Of(BenchMax),
                Weight.Of(DeadliftMax)),
            program,
            Now,
            Now);
    }

    public static WorkoutSession StartFirstWorkout(TrainingCycle cycle, Guid? sessionId = null) =>
        WorkoutSession.StartFromTemplate(
            sessionId ?? Guid.NewGuid(),
            cycle.UserId,
            cycle,
            cycle.Program.Workouts.First(),
            DateOnly.FromDateTime(Now.UtcDateTime),
            Now,
            Now,
            Guid.NewGuid,
            Guid.NewGuid);
}
