namespace GriffGym.Domain.Training;

/// <summary>
/// The three competition lifts. These, and only these, have a reference max.
///
/// Kept separate from <see cref="ExerciseCategory"/> on purpose: a category classifies a
/// movement in the catalogue (and admits accessory work), while a lift type is the key of a
/// planning number. Modelling "the squat max" with a type that can also be ACCESSORY would
/// make an impossible state representable.
/// </summary>
public enum LiftType
{
    Squat = 1,
    BenchPress = 2,
    Deadlift = 3,
}

/// <summary>Which lift a movement belongs to. Only the big three feed statistics.</summary>
public enum ExerciseCategory
{
    Squat = 1,
    BenchPress = 2,
    Deadlift = 3,
    Accessory = 4,
}

/// <summary>The role a movement plays inside a single training day.</summary>
public enum ExerciseType
{
    Top = 1,
    BackOff = 2,
    Volume = 3,
    Light = 4,
    Deload = 5,
    Accessory = 6,
}

/// <summary>
/// Where a cycle is in its life.
///
/// There is deliberately no "ready" state: a cycle that exists but has not been trained yet
/// is simply active with no completed sessions.
/// </summary>
public enum TrainingCycleStatus
{
    Active = 1,
    Completed = 2,
}

public enum WorkoutSessionStatus
{
    InProgress = 1,
    Completed = 2,
    Cancelled = 3,
}

/// <summary>Weeks 1-5 accumulate, week 6 sheds fatigue at half the reference max.</summary>
public enum TrainingWeekType
{
    Training = 1,
    Deload = 2,
}

public static class LiftTypes
{
    public static readonly IReadOnlyList<LiftType> All =
        [LiftType.Squat, LiftType.Deadlift, LiftType.BenchPress];

    public static ExerciseCategory ToCategory(this LiftType lift) => lift switch
    {
        LiftType.Squat => ExerciseCategory.Squat,
        LiftType.BenchPress => ExerciseCategory.BenchPress,
        LiftType.Deadlift => ExerciseCategory.Deadlift,
        _ => throw new ArgumentOutOfRangeException(nameof(lift), lift, "Unknown lift."),
    };
}

public static class ExerciseTypes
{
    /// <summary>Only main-lift work counts towards strength progression statistics.</summary>
    public static bool IsMainLift(this ExerciseType type) => type != ExerciseType.Accessory;
}

public static class WorkoutSessionStatuses
{
    public static bool IsFinished(this WorkoutSessionStatus status) =>
        status != WorkoutSessionStatus.InProgress;
}
