using GriffGym.Api.Contracts.V1;
using GriffGym.Application.Workouts;
using GriffGym.Domain.Training;

namespace GriffGym.Api.Mapping;

internal static class WorkoutMapping
{
    public static CreateWorkoutSessionCommand ToCommand(this CreateWorkoutRequest request) => new(
        request.Id,
        request.TrainingCycleId,
        request.TrainingWeekId,
        request.WorkoutTemplateId,
        request.WeekNumber,
        request.DayNumber,
        request.Title,
        request.IsDeload,
        // Starting a workout is overwhelmingly the common case, so it is the default. Uploading
        // finished history is the deliberate one and has to say so.
        request.Status ?? WorkoutSessionStatus.InProgress,
        request.PerformedOn,
        request.StartedAtUtc,
        request.FinishedAtUtc,
        request.Notes,
        request.Exercises is null ? null : [.. request.Exercises.Select(ToInput)]);

    public static UpdateWorkoutSessionCommand ToCommand(
        this UpdateWorkoutRequest request,
        Guid sessionId) =>
        new(
            sessionId,
            request.ExpectedVersion,
            request.Notes,
            request.Exercises is null ? null : [.. request.Exercises.Select(ToInput)]);

    public static LogSetCommand ToCommand(
        this LogSetRequest request,
        Guid sessionId,
        Guid setLogId) =>
        new(
            sessionId,
            setLogId,
            request.ExpectedVersion,
            request.WeightKg,
            request.Reps,
            request.Rpe,
            request.Completed,
            request.Notes);

    public static FinishWorkoutSessionCommand ToCommand(
        this FinishWorkoutRequest? request,
        Guid sessionId) =>
        new(sessionId, request?.ExpectedVersion, request?.FinishedAtUtc);

    private static ExerciseLogInput ToInput(this ExerciseLogRequest request) => new(
        request.Id,
        request.Position,
        request.ExerciseId,
        request.ExerciseName,
        request.ExerciseCategory,
        request.Type,
        request.Notes,
        [.. request.Sets.Select(ToInput)]);

    private static SetLogInput ToInput(this SetLogRequest request) => new(
        request.Id,
        request.Position,
        request.PlannedWeightKg,
        request.PlannedReps,
        request.PlannedRpeMin,
        request.PlannedRpeMax,
        request.ActualWeightKg,
        request.ActualReps,
        request.ActualRpe,
        request.Completed,
        request.Notes);

    public static WorkoutResponse ToResponse(this WorkoutSessionView view) => new(
        view.Id,
        view.TrainingCycleId,
        view.TrainingWeekId,
        view.WorkoutTemplateId,
        view.WeekNumber,
        view.DayNumber,
        view.Title,
        view.IsDeload,
        view.Status,
        view.PerformedOn,
        view.StartedAtUtc,
        view.FinishedAtUtc,
        view.DurationSeconds,
        view.TotalVolumeKg,
        view.TotalSets,
        view.CompletedSets,
        view.TotalReps,
        view.Notes,
        view.CreatedAtUtc,
        view.UpdatedAtUtc,
        view.Version,
        view.SyncVersion,
        [.. view.Exercises.Select(ToResponse)]);

    public static WorkoutSummaryResponse ToResponse(this WorkoutSessionSummaryView view) => new(
        view.Id,
        view.TrainingCycleId,
        view.WeekNumber,
        view.DayNumber,
        view.Title,
        view.IsDeload,
        view.Status,
        view.PerformedOn,
        view.StartedAtUtc,
        view.FinishedAtUtc,
        view.DurationSeconds,
        view.TotalVolumeKg,
        view.TotalSets,
        view.CompletedSets,
        view.TotalReps,
        view.UpdatedAtUtc,
        view.Version,
        view.SyncVersion);

    private static ExerciseLogResponse ToResponse(this ExerciseLogView view) => new(
        view.Id,
        view.Position,
        view.ExerciseId,
        view.ExerciseName,
        view.ExerciseCategory,
        view.Type,
        view.Notes,
        view.VolumeKg,
        view.BestEstimatedOneRepMaxKg,
        [.. view.Sets.Select(ToResponse)]);

    private static SetLogResponse ToResponse(this SetLogView view) => new(
        view.Id,
        view.Position,
        view.PlannedWeightKg,
        view.PlannedReps,
        view.PlannedRpeMin,
        view.PlannedRpeMax,
        view.ActualWeightKg,
        view.ActualReps,
        view.ActualRpe,
        view.Completed,
        view.Notes,
        view.VolumeKg,
        view.EstimatedOneRepMaxKg);
}
