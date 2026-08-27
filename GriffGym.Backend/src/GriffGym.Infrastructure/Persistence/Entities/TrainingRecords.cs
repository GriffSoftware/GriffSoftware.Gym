using GriffGym.Domain.Training;

namespace GriffGym.Infrastructure.Persistence.Entities;

internal sealed class ExerciseRecord : ISyncable
{
    public Guid Id { get; set; }

    public Guid UserId { get; set; }

    public string Name { get; set; } = string.Empty;

    public ExerciseCategory Category { get; set; }

    public int Version { get; set; }

    public long SyncVersion { get; set; }

    public DateTimeOffset CreatedAtUtc { get; set; }

    public DateTimeOffset UpdatedAtUtc { get; set; }

    public DateTimeOffset? DeletedAtUtc { get; set; }
}

internal sealed class ReferenceMaxRecord : ISyncable
{
    public Guid Id { get; set; }

    public Guid UserId { get; set; }

    public LiftType Lift { get; set; }

    public decimal ValueKg { get; set; }

    public int Version { get; set; }

    public long SyncVersion { get; set; }

    public DateTimeOffset CreatedAtUtc { get; set; }

    public DateTimeOffset UpdatedAtUtc { get; set; }

    public DateTimeOffset? DeletedAtUtc { get; set; }
}

/// <summary>
/// The three planning numbers are three columns, not a serialised blob: a database browser
/// should be able to answer "what was cycle 4 built on?" without decoding anything.
/// </summary>
internal sealed class TrainingCycleRecord : ISyncable
{
    public Guid Id { get; set; }

    public Guid UserId { get; set; }

    public int CycleNumber { get; set; }

    public TrainingCycleStatus Status { get; set; }

    public decimal SquatReferenceMaxKg { get; set; }

    public decimal BenchPressReferenceMaxKg { get; set; }

    public decimal DeadliftReferenceMaxKg { get; set; }

    public DateTimeOffset StartedAtUtc { get; set; }

    public DateTimeOffset? CompletedAtUtc { get; set; }

    public int Version { get; set; }

    public long SyncVersion { get; set; }

    public DateTimeOffset CreatedAtUtc { get; set; }

    public DateTimeOffset UpdatedAtUtc { get; set; }

    public DateTimeOffset? DeletedAtUtc { get; set; }

    public TrainingProgramRecord? Program { get; set; }
}

internal sealed class TrainingProgramRecord
{
    public Guid Id { get; set; }

    public Guid TrainingCycleId { get; set; }

    public string Name { get; set; } = string.Empty;

    /// <summary>
    /// Where the lifter is in the sequence. Null means the plan has run out, which is the same
    /// moment its cycle is finished.
    /// </summary>
    public Guid? CurrentWorkoutTemplateId { get; set; }

    public TrainingCycleRecord? Cycle { get; set; }

    public ICollection<TrainingWeekRecord> Weeks { get; set; } = [];
}

internal sealed class TrainingWeekRecord
{
    public Guid Id { get; set; }

    public Guid TrainingProgramId { get; set; }

    public int WeekNumber { get; set; }

    public string Label { get; set; } = string.Empty;

    public TrainingWeekType Type { get; set; }

    public TrainingProgramRecord? Program { get; set; }

    public ICollection<WorkoutTemplateRecord> Workouts { get; set; } = [];
}

internal sealed class WorkoutTemplateRecord
{
    public Guid Id { get; set; }

    public Guid TrainingWeekId { get; set; }

    public int DayNumber { get; set; }

    /// <summary>Position in the whole program. The plan is a sequence, not a calendar.</summary>
    public int SequenceNumber { get; set; }

    public string Title { get; set; } = string.Empty;

    public TrainingWeekRecord? Week { get; set; }

    public ICollection<ExerciseTemplateRecord> Exercises { get; set; } = [];
}

internal sealed class ExerciseTemplateRecord
{
    public Guid Id { get; set; }

    public Guid WorkoutTemplateId { get; set; }

    public Guid ExerciseId { get; set; }

    /// <summary>Snapshot. Renaming a movement in the catalogue must not rewrite an old plan.</summary>
    public string ExerciseName { get; set; } = string.Empty;

    public ExerciseCategory ExerciseCategory { get; set; }

    public ExerciseType Type { get; set; }

    public int Position { get; set; }

    public WorkoutTemplateRecord? Workout { get; set; }

    public ICollection<PlannedSetRecord> PlannedSets { get; set; } = [];
}

internal sealed class PlannedSetRecord
{
    public Guid Id { get; set; }

    public Guid ExerciseTemplateId { get; set; }

    public int Position { get; set; }

    /// <summary>Null for accessory work, where the plan prescribes reps and RPE but no load.</summary>
    public decimal? WeightKg { get; set; }

    public int? Reps { get; set; }

    public decimal? RpeMin { get; set; }

    public decimal? RpeMax { get; set; }

    public ExerciseTemplateRecord? ExerciseTemplate { get; set; }
}
