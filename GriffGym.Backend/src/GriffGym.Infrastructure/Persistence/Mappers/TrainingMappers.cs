using GriffGym.Domain.Training;
using GriffGym.Infrastructure.Persistence.Entities;

namespace GriffGym.Infrastructure.Persistence.Mappers;

internal static class ExerciseMapper
{
    public static Exercise ToDomain(ExerciseRecord record)
    {
        var exercise = Exercise.FromStorage(
            record.Id,
            record.UserId,
            record.Name,
            record.Category,
            record.CreatedAtUtc,
            record.UpdatedAtUtc);

        exercise.ApplySyncMetadata(
            record.Version,
            record.SyncVersion,
            record.UpdatedAtUtc,
            record.DeletedAtUtc);

        return exercise;
    }

    public static ExerciseRecord ToRecord(Exercise exercise)
    {
        var record = new ExerciseRecord
        {
            Id = exercise.Id,
            UserId = exercise.UserId,
            CreatedAtUtc = exercise.CreatedAtUtc,
        };

        Apply(exercise, record);

        return record;
    }

    public static void Apply(Exercise exercise, ExerciseRecord record)
    {
        record.Name = exercise.Name;
        record.Category = exercise.Category;
        record.DeletedAtUtc = exercise.DeletedAtUtc;
    }
}

internal static class ReferenceMaxMapper
{
    public static ReferenceMax ToDomain(ReferenceMaxRecord record)
    {
        var max = ReferenceMax.FromStorage(
            record.Id,
            record.UserId,
            record.Lift,
            Weight.Of(record.ValueKg),
            record.CreatedAtUtc,
            record.UpdatedAtUtc);

        max.ApplySyncMetadata(
            record.Version,
            record.SyncVersion,
            record.UpdatedAtUtc,
            record.DeletedAtUtc);

        return max;
    }

    public static ReferenceMaxRecord ToRecord(ReferenceMax max)
    {
        var record = new ReferenceMaxRecord
        {
            Id = max.Id,
            UserId = max.UserId,
            Lift = max.Lift,
            CreatedAtUtc = max.CreatedAtUtc,
        };

        Apply(max, record);

        return record;
    }

    public static void Apply(ReferenceMax max, ReferenceMaxRecord record)
    {
        record.ValueKg = max.Value.Kilograms;
        record.DeletedAtUtc = max.DeletedAtUtc;
    }
}

internal static class TrainingCycleMapper
{
    public static TrainingCycle ToDomain(TrainingCycleRecord record)
    {
        var programRecord = record.Program
                            ?? throw new InvalidOperationException(
                                $"Cycle {record.Id} was loaded without its program.");

        var weeks = programRecord.Weeks
            .OrderBy(week => week.WeekNumber)
            .Select(ToDomain)
            .ToList();

        var program = new TrainingProgram(
            programRecord.Id,
            programRecord.Name,
            weeks,
            programRecord.CurrentWorkoutTemplateId);

        var cycle = TrainingCycle.FromStorage(
            record.Id,
            record.UserId,
            record.CycleNumber,
            record.Status,
            // FromStorage rather than Of: a row already on disk is read as it is, even if a
            // zero would be refused for a cycle being created today.
            ReferenceMaxSnapshot.FromStorage(
                Weight.Of(record.SquatReferenceMaxKg),
                Weight.Of(record.BenchPressReferenceMaxKg),
                Weight.Of(record.DeadliftReferenceMaxKg)),
            record.StartedAtUtc,
            record.CompletedAtUtc,
            program,
            record.CreatedAtUtc,
            record.UpdatedAtUtc);

        cycle.ApplySyncMetadata(
            record.Version,
            record.SyncVersion,
            record.UpdatedAtUtc,
            record.DeletedAtUtc);

        return cycle;
    }

    private static TrainingWeek ToDomain(TrainingWeekRecord record) => new(
        record.Id,
        record.WeekNumber,
        record.Label,
        record.Type,
        [.. record.Workouts.OrderBy(workout => workout.DayNumber).Select(ToDomain)]);

    private static WorkoutTemplate ToDomain(WorkoutTemplateRecord record) => new(
        record.Id,
        record.DayNumber,
        record.SequenceNumber,
        record.Title,
        [.. record.Exercises.OrderBy(exercise => exercise.Position).Select(ToDomain)]);

    private static ExerciseTemplate ToDomain(ExerciseTemplateRecord record) => new(
        record.Id,
        record.Position,
        record.ExerciseId,
        record.ExerciseName,
        record.ExerciseCategory,
        record.Type,
        [.. record.PlannedSets.OrderBy(set => set.Position).Select(ToDomain)]);

    private static PlannedSet ToDomain(PlannedSetRecord record) => new(
        record.Id,
        record.Position,
        Weight.OfNullable(record.WeightKg),
        record.Reps,
        RpeTarget.FromBounds(record.RpeMin, record.RpeMax));

    public static TrainingCycleRecord ToRecord(TrainingCycle cycle)
    {
        var record = new TrainingCycleRecord
        {
            Id = cycle.Id,
            UserId = cycle.UserId,
            CycleNumber = cycle.CycleNumber,
            SquatReferenceMaxKg = cycle.ReferenceMaxes.Squat.Kilograms,
            BenchPressReferenceMaxKg = cycle.ReferenceMaxes.BenchPress.Kilograms,
            DeadliftReferenceMaxKg = cycle.ReferenceMaxes.Deadlift.Kilograms,
            StartedAtUtc = cycle.StartedAtUtc,
            CreatedAtUtc = cycle.CreatedAtUtc,
            Program = ToRecord(cycle.Id, cycle.Program),
        };

        Apply(cycle, record);

        return record;
    }

    /// <summary>
    /// Writes back only what a cycle can still change once it exists: its status, its
    /// completion time and where the lifter is in the plan.
    ///
    /// The plan itself is not reconciled, because a plan does not change. Cycle 1 keeps the
    /// program it was actually trained on forever, and there is no code path that could rewrite
    /// it even by accident.
    /// </summary>
    public static void Apply(TrainingCycle cycle, TrainingCycleRecord record)
    {
        record.Status = cycle.Status;
        record.CompletedAtUtc = cycle.CompletedAtUtc;
        record.DeletedAtUtc = cycle.DeletedAtUtc;

        if (record.Program is not null)
        {
            record.Program.CurrentWorkoutTemplateId = cycle.Program.CurrentWorkoutTemplateId;
        }
    }

    private static TrainingProgramRecord ToRecord(Guid cycleId, TrainingProgram program) => new()
    {
        Id = program.Id,
        TrainingCycleId = cycleId,
        Name = program.Name,
        CurrentWorkoutTemplateId = program.CurrentWorkoutTemplateId,
        Weeks = [.. program.Weeks.Select(week => ToRecord(program.Id, week))],
    };

    private static TrainingWeekRecord ToRecord(Guid programId, TrainingWeek week) => new()
    {
        Id = week.Id,
        TrainingProgramId = programId,
        WeekNumber = week.WeekNumber,
        Label = week.Label,
        Type = week.Type,
        Workouts = [.. week.Workouts.Select(workout => ToRecord(week.Id, workout))],
    };

    private static WorkoutTemplateRecord ToRecord(Guid weekId, WorkoutTemplate workout) => new()
    {
        Id = workout.Id,
        TrainingWeekId = weekId,
        DayNumber = workout.DayNumber,
        SequenceNumber = workout.SequenceNumber,
        Title = workout.Title,
        Exercises = [.. workout.Exercises.Select(exercise => ToRecord(workout.Id, exercise))],
    };

    private static ExerciseTemplateRecord ToRecord(Guid workoutId, ExerciseTemplate exercise) => new()
    {
        Id = exercise.Id,
        WorkoutTemplateId = workoutId,
        ExerciseId = exercise.ExerciseId,
        ExerciseName = exercise.ExerciseName,
        ExerciseCategory = exercise.ExerciseCategory,
        Type = exercise.Type,
        Position = exercise.Position,
        PlannedSets = [.. exercise.PlannedSets.Select(set => ToRecord(exercise.Id, set))],
    };

    private static PlannedSetRecord ToRecord(Guid exerciseTemplateId, PlannedSet set) => new()
    {
        Id = set.Id,
        ExerciseTemplateId = exerciseTemplateId,
        Position = set.Position,
        WeightKg = set.Weight?.Kilograms,
        Reps = set.Reps,
        RpeMin = set.TargetRpe?.Min.Value,
        RpeMax = set.TargetRpe?.Max.Value,
    };
}
