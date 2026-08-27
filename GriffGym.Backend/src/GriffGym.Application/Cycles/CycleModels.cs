using GriffGym.Application.Exercises;
using GriffGym.Domain.Training;

namespace GriffGym.Application.Cycles;

/*
 * Creating a cycle is one request carrying the whole plan: the cycle, the exercises it refers
 * to, six weeks, their workouts, and every prescribed set. Not because a big payload is
 * elegant, but because a cycle only means anything with its plan attached — and because the
 * phone generates all of it locally before it ever talks to the server.
 */

public sealed record CreateTrainingCycleCommand(
    Guid? Id,
    int CycleNumber,
    decimal SquatReferenceMaxKg,
    decimal BenchPressReferenceMaxKg,
    decimal DeadliftReferenceMaxKg,
    DateTimeOffset StartedAtUtc,
    IReadOnlyList<ExerciseInput> Exercises,
    TrainingProgramInput Program);

public sealed record TrainingProgramInput(
    Guid? Id,
    string Name,
    Guid? CurrentWorkoutTemplateId,
    IReadOnlyList<TrainingWeekInput> Weeks);

public sealed record TrainingWeekInput(
    Guid? Id,
    int WeekNumber,
    string Label,
    TrainingWeekType Type,
    IReadOnlyList<WorkoutTemplateInput> Workouts);

public sealed record WorkoutTemplateInput(
    Guid? Id,
    int DayNumber,
    int SequenceNumber,
    string Title,
    IReadOnlyList<ExerciseTemplateInput> Exercises);

/// <summary>
/// <c>ExerciseName</c> and <c>ExerciseCategory</c> are optional: when a client omits them they
/// are taken from the catalogue entry <c>ExerciseId</c> points at. Once written they are a
/// snapshot and stop tracking the catalogue.
/// </summary>
public sealed record ExerciseTemplateInput(
    Guid? Id,
    int Position,
    Guid ExerciseId,
    string? ExerciseName,
    ExerciseCategory? ExerciseCategory,
    ExerciseType Type,
    IReadOnlyList<PlannedSetInput> PlannedSets);

public sealed record PlannedSetInput(
    Guid? Id,
    int Position,
    decimal? WeightKg,
    int? Reps,
    decimal? RpeMin,
    decimal? RpeMax);

public sealed record CompleteTrainingCycleCommand(Guid CycleId, DateTimeOffset? CompletedAtUtc);

public sealed record UpdateCycleProgressCommand(Guid CycleId, Guid? CurrentWorkoutTemplateId);

public sealed record ReferenceMaxSnapshotView(
    decimal SquatKg,
    decimal BenchPressKg,
    decimal DeadliftKg);

public sealed record TrainingCycleView(
    Guid Id,
    int CycleNumber,
    TrainingCycleStatus Status,
    ReferenceMaxSnapshotView ReferenceMaxes,
    DateTimeOffset StartedAtUtc,
    DateTimeOffset? CompletedAtUtc,
    DateTimeOffset CreatedAtUtc,
    DateTimeOffset UpdatedAtUtc,
    int Version,
    long SyncVersion,
    TrainingProgramView Program);

public sealed record TrainingProgramView(
    Guid Id,
    string Name,
    Guid? CurrentWorkoutTemplateId,
    IReadOnlyList<TrainingWeekView> Weeks);

public sealed record TrainingWeekView(
    Guid Id,
    int WeekNumber,
    string Label,
    TrainingWeekType Type,
    bool IsDeload,
    IReadOnlyList<WorkoutTemplateView> Workouts);

public sealed record WorkoutTemplateView(
    Guid Id,
    int DayNumber,
    int SequenceNumber,
    string Title,
    IReadOnlyList<ExerciseTemplateView> Exercises);

public sealed record ExerciseTemplateView(
    Guid Id,
    int Position,
    Guid ExerciseId,
    string ExerciseName,
    ExerciseCategory ExerciseCategory,
    ExerciseType Type,
    IReadOnlyList<PlannedSetView> PlannedSets);

public sealed record PlannedSetView(
    Guid Id,
    int Position,
    decimal? WeightKg,
    int? Reps,
    decimal? RpeMin,
    decimal? RpeMax);

/// <summary>
/// A cycle together with what actually happened inside it.
///
/// Progress is counted from completed sessions rather than tracked as its own state, so it
/// cannot drift away from the training log.
/// </summary>
public sealed record TrainingCycleSummaryView(
    Guid Id,
    int CycleNumber,
    TrainingCycleStatus Status,
    ReferenceMaxSnapshotView ReferenceMaxes,
    DateTimeOffset StartedAtUtc,
    DateTimeOffset? CompletedAtUtc,
    DateTimeOffset CreatedAtUtc,
    DateTimeOffset UpdatedAtUtc,
    int Version,
    long SyncVersion,
    Guid ProgramId,
    string ProgramName,
    Guid? CurrentWorkoutTemplateId,
    IReadOnlyList<CycleWeekProgressView> Weeks)
{
    public int PlannedWorkouts => Weeks.Sum(week => week.PlannedWorkouts);

    public int CompletedWorkouts => Weeks.Sum(week => week.CompletedWorkouts);

    public int CompletedWeeks => Weeks.Count(week => week.IsComplete);

    /// <summary>The week the lifter is in: the first one that is not finished.</summary>
    public int? CurrentWeekNumber =>
        Weeks.FirstOrDefault(week => !week.IsComplete)?.WeekNumber;
}

public sealed record CycleWeekProgressView(
    Guid Id,
    int WeekNumber,
    string Label,
    bool IsDeload,
    int PlannedWorkouts,
    int CompletedWorkouts)
{
    public bool IsComplete => PlannedWorkouts > 0 && CompletedWorkouts >= PlannedWorkouts;

    public bool IsStarted => CompletedWorkouts > 0;
}
