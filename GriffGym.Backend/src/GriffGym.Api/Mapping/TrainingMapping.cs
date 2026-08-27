using GriffGym.Api.Contracts.V1;
using GriffGym.Application.Cycles;
using GriffGym.Application.Exercises;
using GriffGym.Application.ReferenceMaxes;
using GriffGym.Domain.Training;

namespace GriffGym.Api.Mapping;

internal static class TrainingMapping
{
    public static ReferenceMaxResponse ToResponse(this ReferenceMaxView view) => new(
        view.Id,
        view.Lift,
        view.ValueKg,
        view.CreatedAtUtc,
        view.UpdatedAtUtc,
        view.Version,
        view.SyncVersion);

    /// <summary>The lift is the route value, never anything the body claimed.</summary>
    public static UpdateReferenceMaxCommand ToCommand(
        this UpdateReferenceMaxRequest request,
        LiftType lift) =>
        new(lift, request.ValueKg, request.Id);

    public static ExerciseResponse ToResponse(this ExerciseView view) => new(
        view.Id,
        view.Name,
        view.Category,
        view.CreatedAtUtc,
        view.UpdatedAtUtc,
        view.Version,
        view.SyncVersion);

    public static ExerciseInput ToInput(this ExerciseRequest request) =>
        new(request.Id, request.Name, request.Category);

    public static CreateTrainingCycleCommand ToCommand(this CreateCycleRequest request) => new(
        request.Id,
        request.CycleNumber,
        request.SquatReferenceMaxKg,
        request.BenchPressReferenceMaxKg,
        request.DeadliftReferenceMaxKg,
        request.StartedAtUtc,
        [.. request.Exercises.Select(ToInput)],
        request.Program.ToInput());

    private static TrainingProgramInput ToInput(this ProgramRequest request) => new(
        request.Id,
        request.Name,
        request.CurrentWorkoutTemplateId,
        [.. request.Weeks.Select(ToInput)]);

    private static TrainingWeekInput ToInput(this WeekRequest request) => new(
        request.Id,
        request.WeekNumber,
        request.Label,
        request.Type,
        [.. request.Workouts.Select(ToInput)]);

    private static WorkoutTemplateInput ToInput(this WorkoutTemplateRequest request) => new(
        request.Id,
        request.DayNumber,
        request.SequenceNumber,
        request.Title,
        [.. request.Exercises.Select(ToInput)]);

    private static ExerciseTemplateInput ToInput(this ExerciseTemplateRequest request) => new(
        request.Id,
        request.Position,
        request.ExerciseId,
        request.ExerciseName,
        request.ExerciseCategory,
        request.Type,
        [.. request.PlannedSets.Select(ToInput)]);

    private static PlannedSetInput ToInput(this PlannedSetRequest request) => new(
        request.Id,
        request.Position,
        request.WeightKg,
        request.Reps,
        request.RpeMin,
        request.RpeMax);

    public static CycleResponse ToResponse(this TrainingCycleView view) => new(
        view.Id,
        view.CycleNumber,
        view.Status,
        view.ReferenceMaxes.ToResponse(),
        view.StartedAtUtc,
        view.CompletedAtUtc,
        view.CreatedAtUtc,
        view.UpdatedAtUtc,
        view.Version,
        view.SyncVersion,
        view.Program.ToResponse());

    private static ReferenceMaxSnapshotResponse ToResponse(this ReferenceMaxSnapshotView view) =>
        new(view.SquatKg, view.BenchPressKg, view.DeadliftKg);

    private static ProgramResponse ToResponse(this TrainingProgramView view) => new(
        view.Id,
        view.Name,
        view.CurrentWorkoutTemplateId,
        [.. view.Weeks.Select(ToResponse)]);

    private static WeekResponse ToResponse(this TrainingWeekView view) => new(
        view.Id,
        view.WeekNumber,
        view.Label,
        view.Type,
        view.IsDeload,
        [.. view.Workouts.Select(ToResponse)]);

    private static WorkoutTemplateResponse ToResponse(this WorkoutTemplateView view) => new(
        view.Id,
        view.DayNumber,
        view.SequenceNumber,
        view.Title,
        [.. view.Exercises.Select(ToResponse)]);

    private static ExerciseTemplateResponse ToResponse(this ExerciseTemplateView view) => new(
        view.Id,
        view.Position,
        view.ExerciseId,
        view.ExerciseName,
        view.ExerciseCategory,
        view.Type,
        [.. view.PlannedSets.Select(ToResponse)]);

    private static PlannedSetResponse ToResponse(this PlannedSetView view) => new(
        view.Id,
        view.Position,
        view.WeightKg,
        view.Reps,
        view.RpeMin,
        view.RpeMax);

    public static CycleSummaryResponse ToResponse(this TrainingCycleSummaryView view) => new(
        view.Id,
        view.CycleNumber,
        view.Status,
        view.ReferenceMaxes.ToResponse(),
        view.StartedAtUtc,
        view.CompletedAtUtc,
        view.CreatedAtUtc,
        view.UpdatedAtUtc,
        view.Version,
        view.SyncVersion,
        view.ProgramId,
        view.ProgramName,
        view.CurrentWorkoutTemplateId,
        view.PlannedWorkouts,
        view.CompletedWorkouts,
        view.CompletedWeeks,
        view.CurrentWeekNumber,
        [.. view.Weeks.Select(ToResponse)]);

    private static CycleWeekProgressResponse ToResponse(this CycleWeekProgressView view) => new(
        view.Id,
        view.WeekNumber,
        view.Label,
        view.IsDeload,
        view.PlannedWorkouts,
        view.CompletedWorkouts,
        view.IsComplete,
        view.IsStarted);
}
