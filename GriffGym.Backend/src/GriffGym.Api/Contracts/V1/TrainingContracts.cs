using GriffGym.Domain.Training;

namespace GriffGym.Api.Contracts.V1;

public sealed record ReferenceMaxResponse(
    Guid Id,
    LiftType Lift,
    decimal ValueKg,
    DateTimeOffset CreatedAtUtc,
    DateTimeOffset UpdatedAtUtc,
    int Version,
    long SyncVersion);

/// <summary>
/// The lift comes from the route, never from this body — a request cannot claim to be updating
/// the squat while carrying a bench payload. <c>Id</c> is optional and lets a phone keep the
/// identifier it already generated locally.
/// </summary>
public sealed record UpdateReferenceMaxRequest(decimal ValueKg, Guid? Id);

public sealed record ExerciseResponse(
    Guid Id,
    string Name,
    ExerciseCategory Category,
    DateTimeOffset CreatedAtUtc,
    DateTimeOffset UpdatedAtUtc,
    int Version,
    long SyncVersion);

public sealed record ExerciseRequest(Guid Id, string Name, ExerciseCategory Category);

// ---------------------------------------------------------------------------------------------
// Cycles
// ---------------------------------------------------------------------------------------------

/// <summary>
/// One request carrying a whole cycle: the planning numbers it was built from, the movements it
/// refers to, and the full six-week plan.
///
/// Not a chatty sequence of calls, because the phone generates all of this locally before it
/// ever talks to the server, and a cycle that exists without its plan is not a cycle.
/// </summary>
public sealed record CreateCycleRequest(
    Guid? Id,
    int CycleNumber,
    decimal SquatReferenceMaxKg,
    decimal BenchPressReferenceMaxKg,
    decimal DeadliftReferenceMaxKg,
    DateTimeOffset StartedAtUtc,
    IReadOnlyList<ExerciseRequest> Exercises,
    ProgramRequest Program);

public sealed record ProgramRequest(
    Guid? Id,
    string Name,
    Guid? CurrentWorkoutTemplateId,
    IReadOnlyList<WeekRequest> Weeks);

public sealed record WeekRequest(
    Guid? Id,
    int WeekNumber,
    string Label,
    TrainingWeekType Type,
    IReadOnlyList<WorkoutTemplateRequest> Workouts);

public sealed record WorkoutTemplateRequest(
    Guid? Id,
    int DayNumber,
    int SequenceNumber,
    string Title,
    IReadOnlyList<ExerciseTemplateRequest> Exercises);

public sealed record ExerciseTemplateRequest(
    Guid? Id,
    int Position,
    Guid ExerciseId,
    string? ExerciseName,
    ExerciseCategory? ExerciseCategory,
    ExerciseType Type,
    IReadOnlyList<PlannedSetRequest> PlannedSets);

public sealed record PlannedSetRequest(
    Guid? Id,
    int Position,
    decimal? WeightKg,
    int? Reps,
    decimal? RpeMin,
    decimal? RpeMax);

public sealed record CompleteCycleRequest(DateTimeOffset? CompletedAtUtc);

public sealed record UpdateCycleProgressRequest(Guid? CurrentWorkoutTemplateId);

public sealed record ReferenceMaxSnapshotResponse(
    decimal SquatKg,
    decimal BenchPressKg,
    decimal DeadliftKg);

public sealed record CycleResponse(
    Guid Id,
    int CycleNumber,
    TrainingCycleStatus Status,
    ReferenceMaxSnapshotResponse ReferenceMaxes,
    DateTimeOffset StartedAtUtc,
    DateTimeOffset? CompletedAtUtc,
    DateTimeOffset CreatedAtUtc,
    DateTimeOffset UpdatedAtUtc,
    int Version,
    long SyncVersion,
    ProgramResponse Program);

public sealed record ProgramResponse(
    Guid Id,
    string Name,
    Guid? CurrentWorkoutTemplateId,
    IReadOnlyList<WeekResponse> Weeks);

public sealed record WeekResponse(
    Guid Id,
    int WeekNumber,
    string Label,
    TrainingWeekType Type,
    bool IsDeload,
    IReadOnlyList<WorkoutTemplateResponse> Workouts);

public sealed record WorkoutTemplateResponse(
    Guid Id,
    int DayNumber,
    int SequenceNumber,
    string Title,
    IReadOnlyList<ExerciseTemplateResponse> Exercises);

public sealed record ExerciseTemplateResponse(
    Guid Id,
    int Position,
    Guid ExerciseId,
    string ExerciseName,
    ExerciseCategory ExerciseCategory,
    ExerciseType Type,
    IReadOnlyList<PlannedSetResponse> PlannedSets);

public sealed record PlannedSetResponse(
    Guid Id,
    int Position,
    decimal? WeightKg,
    int? Reps,
    decimal? RpeMin,
    decimal? RpeMax);

/// <summary>
/// A cycle with how far through it the lifter got, counted from completed sessions rather than
/// tracked separately — so it cannot drift away from the training log.
/// </summary>
public sealed record CycleSummaryResponse(
    Guid Id,
    int CycleNumber,
    TrainingCycleStatus Status,
    ReferenceMaxSnapshotResponse ReferenceMaxes,
    DateTimeOffset StartedAtUtc,
    DateTimeOffset? CompletedAtUtc,
    DateTimeOffset CreatedAtUtc,
    DateTimeOffset UpdatedAtUtc,
    int Version,
    long SyncVersion,
    Guid ProgramId,
    string ProgramName,
    Guid? CurrentWorkoutTemplateId,
    int PlannedWorkouts,
    int CompletedWorkouts,
    int CompletedWeeks,
    int? CurrentWeekNumber,
    IReadOnlyList<CycleWeekProgressResponse> Weeks);

public sealed record CycleWeekProgressResponse(
    Guid Id,
    int WeekNumber,
    string Label,
    bool IsDeload,
    int PlannedWorkouts,
    int CompletedWorkouts,
    bool IsComplete,
    bool IsStarted);
