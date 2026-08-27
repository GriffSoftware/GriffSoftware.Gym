using GriffGym.Domain.Training;

namespace GriffGym.Application.Workouts;

/// <summary>
/// Creates a session, either way round.
///
/// With a <paramref name="WorkoutTemplateId"/> and no exercises, the server snapshots the
/// planned unit out of the cycle — the ordinary "press START" path. With exercises supplied,
/// the client is uploading a session it already holds: one it started offline, or one from the
/// months of history it accumulated before there was an account to sync to.
/// </summary>
public sealed record CreateWorkoutSessionCommand(
    Guid? Id,
    Guid? TrainingCycleId,
    Guid? TrainingWeekId,
    Guid? WorkoutTemplateId,
    int? WeekNumber,
    int? DayNumber,
    string? Title,
    bool? IsDeload,
    WorkoutSessionStatus Status,
    DateOnly? PerformedOn,
    DateTimeOffset? StartedAtUtc,
    DateTimeOffset? FinishedAtUtc,
    string? Notes,
    IReadOnlyList<ExerciseLogInput>? Exercises);

public sealed record ExerciseLogInput(
    Guid? Id,
    int Position,
    Guid? ExerciseId,
    string? ExerciseName,
    ExerciseCategory? ExerciseCategory,
    ExerciseType Type,
    string? Notes,
    IReadOnlyList<SetLogInput> Sets);

public sealed record SetLogInput(
    Guid? Id,
    int Position,
    decimal? PlannedWeightKg,
    int? PlannedReps,
    decimal? PlannedRpeMin,
    decimal? PlannedRpeMax,
    decimal? ActualWeightKg,
    int? ActualReps,
    decimal? ActualRpe,
    bool Completed,
    string? Notes);

/// <summary>
/// Replaces the mutable part of a live session.
///
/// <paramref name="ExpectedVersion"/> is the revision the client believes it holds. If another
/// device has written since, the update is refused instead of quietly winning — a lost workout
/// is not an acceptable outcome of two phones being open at once.
/// </summary>
public sealed record UpdateWorkoutSessionCommand(
    Guid Id,
    int? ExpectedVersion,
    string? Notes,
    IReadOnlyList<ExerciseLogInput>? Exercises);

/// <summary>One set, logged as the lifter finishes it. The fast path during a workout.</summary>
public sealed record LogSetCommand(
    Guid SessionId,
    Guid SetLogId,
    int? ExpectedVersion,
    decimal? WeightKg,
    int? Reps,
    decimal? Rpe,
    bool Completed,
    string? Notes);

public sealed record FinishWorkoutSessionCommand(
    Guid Id,
    int? ExpectedVersion,
    DateTimeOffset? FinishedAtUtc);

public sealed record WorkoutSessionView(
    Guid Id,
    Guid? TrainingCycleId,
    Guid? TrainingWeekId,
    Guid? WorkoutTemplateId,
    int WeekNumber,
    int DayNumber,
    string Title,
    bool IsDeload,
    WorkoutSessionStatus Status,
    DateOnly PerformedOn,
    DateTimeOffset StartedAtUtc,
    DateTimeOffset? FinishedAtUtc,
    long? DurationSeconds,
    decimal TotalVolumeKg,
    int TotalSets,
    int CompletedSets,
    int TotalReps,
    string? Notes,
    DateTimeOffset CreatedAtUtc,
    DateTimeOffset UpdatedAtUtc,
    int Version,
    long SyncVersion,
    IReadOnlyList<ExerciseLogView> Exercises);

/// <summary>
/// A session without its sets, for list endpoints.
///
/// Three years of history is thousands of set rows; a page of twenty sessions should not drag
/// them all across the wire when the caller is drawing a list.
/// </summary>
public sealed record WorkoutSessionSummaryView(
    Guid Id,
    Guid? TrainingCycleId,
    int WeekNumber,
    int DayNumber,
    string Title,
    bool IsDeload,
    WorkoutSessionStatus Status,
    DateOnly PerformedOn,
    DateTimeOffset StartedAtUtc,
    DateTimeOffset? FinishedAtUtc,
    long? DurationSeconds,
    decimal TotalVolumeKg,
    int TotalSets,
    int CompletedSets,
    int TotalReps,
    DateTimeOffset UpdatedAtUtc,
    int Version,
    long SyncVersion);

public sealed record ExerciseLogView(
    Guid Id,
    int Position,
    Guid? ExerciseId,
    string ExerciseName,
    ExerciseCategory ExerciseCategory,
    ExerciseType Type,
    string? Notes,
    decimal VolumeKg,
    decimal? BestEstimatedOneRepMaxKg,
    IReadOnlyList<SetLogView> Sets);

/// <summary>
/// Planned and actual side by side, never merged.
///
/// "Squat TOP, 1 x 3 x 192.5" is what was asked for; "192.5 kg, 3 reps, RPE 8.5, done" is what
/// happened. A client needs both to show a workout honestly, and nothing on the write path is
/// allowed to let one overwrite the other.
/// </summary>
public sealed record SetLogView(
    Guid Id,
    int Position,
    decimal? PlannedWeightKg,
    int? PlannedReps,
    decimal? PlannedRpeMin,
    decimal? PlannedRpeMax,
    decimal? ActualWeightKg,
    int? ActualReps,
    decimal? ActualRpe,
    bool Completed,
    string? Notes,
    decimal VolumeKg,
    decimal? EstimatedOneRepMaxKg);
