using GriffGym.Domain.Workouts;

namespace GriffGym.Application.Workouts;

internal static class WorkoutMapper
{
    public static WorkoutSessionView ToView(WorkoutSession session) => new(
        session.Id,
        session.TrainingCycleId,
        session.TrainingWeekId,
        session.WorkoutTemplateId,
        session.WeekNumber,
        session.DayNumber,
        session.Title,
        session.IsDeload,
        session.Status,
        session.PerformedOn,
        session.StartedAtUtc,
        session.FinishedAtUtc,
        (long?)session.Duration?.TotalSeconds,
        session.TotalVolume.Kilograms,
        session.TotalSets,
        session.CompletedSets,
        session.TotalReps,
        session.Notes,
        session.CreatedAtUtc,
        session.UpdatedAtUtc,
        session.Version,
        session.SyncVersion,
        [.. session.Exercises.Select(ToView)]);

    public static WorkoutSessionSummaryView ToSummary(WorkoutSession session) => new(
        session.Id,
        session.TrainingCycleId,
        session.WeekNumber,
        session.DayNumber,
        session.Title,
        session.IsDeload,
        session.Status,
        session.PerformedOn,
        session.StartedAtUtc,
        session.FinishedAtUtc,
        (long?)session.Duration?.TotalSeconds,
        session.TotalVolume.Kilograms,
        session.TotalSets,
        session.CompletedSets,
        session.TotalReps,
        session.UpdatedAtUtc,
        session.Version,
        session.SyncVersion);

    public static ExerciseLogView ToView(ExerciseLog exercise) => new(
        exercise.Id,
        exercise.Position,
        exercise.ExerciseId,
        exercise.ExerciseName,
        exercise.ExerciseCategory,
        exercise.Type,
        exercise.Notes,
        exercise.Volume.Kilograms,
        exercise.BestEstimatedOneRepMax?.Kilograms,
        [.. exercise.Sets.Select(ToView)]);

    public static SetLogView ToView(SetLog set) => new(
        set.Id,
        set.Position,
        set.PlannedWeight?.Kilograms,
        set.PlannedReps,
        set.PlannedRpe?.Min.Value,
        set.PlannedRpe?.Max.Value,
        set.ActualWeight?.Kilograms,
        set.ActualReps,
        set.ActualRpe?.Value,
        set.Completed,
        set.Notes,
        set.Volume.Kilograms,
        set.EstimatedOneRepMax?.Kilograms);
}
