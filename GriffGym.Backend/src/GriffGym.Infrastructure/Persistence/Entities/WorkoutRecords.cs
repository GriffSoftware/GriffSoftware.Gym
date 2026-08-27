using GriffGym.Domain.Training;

namespace GriffGym.Infrastructure.Persistence.Entities;

/// <summary>
/// A performed workout.
///
/// Week, day, title and the deload flag are copied in rather than read through
/// <see cref="WorkoutTemplateId"/>: this row is a snapshot, and editing the program must never
/// rewrite history. The link back to the template is provenance only and is nulled rather than
/// cascaded if the template ever disappears.
/// </summary>
internal sealed class WorkoutSessionRecord : ISyncable
{
    public Guid Id { get; set; }

    public Guid UserId { get; set; }

    public Guid? TrainingCycleId { get; set; }

    public Guid? TrainingWeekId { get; set; }

    public Guid? WorkoutTemplateId { get; set; }

    public int WeekNumber { get; set; }

    public int DayNumber { get; set; }

    public string Title { get; set; } = string.Empty;

    public bool IsDeload { get; set; }

    public WorkoutSessionStatus Status { get; set; }

    public DateOnly PerformedOn { get; set; }

    public DateTimeOffset StartedAtUtc { get; set; }

    public DateTimeOffset? FinishedAtUtc { get; set; }

    /// <summary>Tonnage frozen at completion; null while the session is still live.</summary>
    public decimal? TotalVolumeKg { get; set; }

    public string? Notes { get; set; }

    public int Version { get; set; }

    public long SyncVersion { get; set; }

    public DateTimeOffset CreatedAtUtc { get; set; }

    public DateTimeOffset UpdatedAtUtc { get; set; }

    public DateTimeOffset? DeletedAtUtc { get; set; }

    public ICollection<ExerciseLogRecord> Exercises { get; set; } = [];
}

internal sealed class ExerciseLogRecord
{
    public Guid Id { get; set; }

    public Guid WorkoutSessionId { get; set; }

    /// <summary>Nullable: removing a movement from the catalogue must not take the log with it.</summary>
    public Guid? ExerciseId { get; set; }

    public string ExerciseName { get; set; } = string.Empty;

    public ExerciseCategory ExerciseCategory { get; set; }

    public ExerciseType Type { get; set; }

    public int Position { get; set; }

    public string? Notes { get; set; }

    public WorkoutSessionRecord? Session { get; set; }

    public ICollection<SetLogRecord> Sets { get; set; } = [];
}

/// <summary>
/// One logged set: planned columns next to actual ones, never merged.
///
/// The planned values are a snapshot taken when the session started; nothing on the write path
/// lets an actual value overwrite a planned one.
/// </summary>
internal sealed class SetLogRecord
{
    public Guid Id { get; set; }

    public Guid ExerciseLogId { get; set; }

    public int Position { get; set; }

    public decimal? PlannedWeightKg { get; set; }

    public int? PlannedReps { get; set; }

    public decimal? PlannedRpeMin { get; set; }

    public decimal? PlannedRpeMax { get; set; }

    public decimal? ActualWeightKg { get; set; }

    public int? ActualReps { get; set; }

    public decimal? ActualRpe { get; set; }

    public bool Completed { get; set; }

    public string? Notes { get; set; }

    public ExerciseLogRecord? ExerciseLog { get; set; }
}
