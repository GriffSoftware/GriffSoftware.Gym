using GriffGym.Domain.Training;

namespace GriffGym.Application.Cycles;

internal static class CycleMapper
{
    public static TrainingCycleView ToView(TrainingCycle cycle) => new(
        cycle.Id,
        cycle.CycleNumber,
        cycle.Status,
        ToView(cycle.ReferenceMaxes),
        cycle.StartedAtUtc,
        cycle.CompletedAtUtc,
        cycle.CreatedAtUtc,
        cycle.UpdatedAtUtc,
        cycle.Version,
        cycle.SyncVersion,
        ToView(cycle.Program));

    public static ReferenceMaxSnapshotView ToView(ReferenceMaxSnapshot snapshot) => new(
        snapshot.Squat.Kilograms,
        snapshot.BenchPress.Kilograms,
        snapshot.Deadlift.Kilograms);

    public static TrainingProgramView ToView(TrainingProgram program) => new(
        program.Id,
        program.Name,
        program.CurrentWorkoutTemplateId,
        [.. program.Weeks.Select(ToView)]);

    public static TrainingWeekView ToView(TrainingWeek week) => new(
        week.Id,
        week.WeekNumber,
        week.Label,
        week.Type,
        week.IsDeload,
        [.. week.Workouts.Select(ToView)]);

    public static WorkoutTemplateView ToView(WorkoutTemplate workout) => new(
        workout.Id,
        workout.DayNumber,
        workout.SequenceNumber,
        workout.Title,
        [.. workout.Exercises.Select(ToView)]);

    public static ExerciseTemplateView ToView(ExerciseTemplate exercise) => new(
        exercise.Id,
        exercise.Position,
        exercise.ExerciseId,
        exercise.ExerciseName,
        exercise.ExerciseCategory,
        exercise.Type,
        [.. exercise.PlannedSets.Select(ToView)]);

    public static PlannedSetView ToView(PlannedSet set) => new(
        set.Id,
        set.Position,
        set.Weight?.Kilograms,
        set.Reps,
        set.TargetRpe?.Min.Value,
        set.TargetRpe?.Max.Value);

    /// <summary>
    /// A cycle plus how far through it the lifter got, with the week counts supplied by the
    /// caller so that one query can serve a whole list of cycles.
    /// </summary>
    public static TrainingCycleSummaryView ToSummary(
        TrainingCycle cycle,
        IReadOnlyDictionary<int, int> completedByWeek) =>
        new(
            cycle.Id,
            cycle.CycleNumber,
            cycle.Status,
            ToView(cycle.ReferenceMaxes),
            cycle.StartedAtUtc,
            cycle.CompletedAtUtc,
            cycle.CreatedAtUtc,
            cycle.UpdatedAtUtc,
            cycle.Version,
            cycle.SyncVersion,
            cycle.Program.Id,
            cycle.Program.Name,
            cycle.Program.CurrentWorkoutTemplateId,
            [.. cycle.Program.Weeks.Select(week => new CycleWeekProgressView(
                week.Id,
                week.WeekNumber,
                week.Label,
                week.IsDeload,
                week.Workouts.Count,
                completedByWeek.GetValueOrDefault(week.WeekNumber)))]);
}
