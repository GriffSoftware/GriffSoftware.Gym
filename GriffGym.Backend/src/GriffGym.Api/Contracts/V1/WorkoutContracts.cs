using GriffGym.Domain.Training;

namespace GriffGym.Api.Contracts.V1;

/// <summary>
/// Creates a session, either way round.
///
/// With <c>WorkoutTemplateId</c> and <c>TrainingCycleId</c> and no exercises, the server
/// snapshots the planned unit out of the cycle — the ordinary "press START" path. With
/// exercises supplied, the client is uploading a session it already holds: one it started
/// offline, or one out of the history it accumulated before there was an account to sync to.
///
/// There is no <c>UserId</c> field, here or anywhere else. Ownership comes from the access
/// token and cannot be asserted by a request.
/// </summary>
public sealed record CreateWorkoutRequest(
    Guid? Id,
    Guid? TrainingCycleId,
    Guid? TrainingWeekId,
    Guid? WorkoutTemplateId,
    int? WeekNumber,
    int? DayNumber,
    string? Title,
    bool? IsDeload,
    WorkoutSessionStatus? Status,
    DateOnly? PerformedOn,
    DateTimeOffset? StartedAtUtc,
    DateTimeOffset? FinishedAtUtc,
    string? Notes,
    IReadOnlyList<ExerciseLogRequest>? Exercises);

public sealed record ExerciseLogRequest(
    Guid? Id,
    int Position,
    Guid? ExerciseId,
    string? ExerciseName,
    ExerciseCategory? ExerciseCategory,
    ExerciseType Type,
    string? Notes,
    IReadOnlyList<SetLogRequest> Sets);

public sealed record SetLogRequest(
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
/// <c>ExpectedVersion</c> is the revision the client believes it holds. Sending it turns a
/// blind overwrite into a detected conflict; omitting it means last-write-wins, which is only
/// ever right for a device that knows it is the only one writing.
/// </summary>
public sealed record UpdateWorkoutRequest(
    int? ExpectedVersion,
    string? Notes,
    IReadOnlyList<ExerciseLogRequest>? Exercises);

public sealed record LogSetRequest(
    int? ExpectedVersion,
    decimal? WeightKg,
    int? Reps,
    decimal? Rpe,
    bool Completed,
    string? Notes);

public sealed record FinishWorkoutRequest(int? ExpectedVersion, DateTimeOffset? FinishedAtUtc);

public sealed record WorkoutResponse(
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
    IReadOnlyList<ExerciseLogResponse> Exercises);

/// <summary>A session without its sets. What a list endpoint returns.</summary>
public sealed record WorkoutSummaryResponse(
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

public sealed record ExerciseLogResponse(
    Guid Id,
    int Position,
    Guid? ExerciseId,
    string ExerciseName,
    ExerciseCategory ExerciseCategory,
    ExerciseType Type,
    string? Notes,
    decimal VolumeKg,
    decimal? BestEstimatedOneRepMaxKg,
    IReadOnlyList<SetLogResponse> Sets);

/// <summary>
/// Planned and actual, side by side and never merged. A client needs both to show a workout
/// honestly: "3 x 3 @ 192.5 was the plan; here is what actually went on the bar."
/// </summary>
public sealed record SetLogResponse(
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
