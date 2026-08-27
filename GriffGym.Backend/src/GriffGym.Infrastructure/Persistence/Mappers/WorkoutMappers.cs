using GriffGym.Domain.Training;
using GriffGym.Domain.Workouts;
using GriffGym.Infrastructure.Persistence.Entities;

namespace GriffGym.Infrastructure.Persistence.Mappers;

internal static class WorkoutSessionMapper
{
    public static WorkoutSession ToDomain(WorkoutSessionRecord record)
    {
        var exercises = record.Exercises
            .OrderBy(exercise => exercise.Position)
            .Select(ToDomain)
            .ToList();

        var session = WorkoutSession.FromStorage(
            record.Id,
            record.UserId,
            record.TrainingCycleId,
            record.TrainingWeekId,
            record.WorkoutTemplateId,
            record.WeekNumber,
            record.DayNumber,
            record.Title,
            record.IsDeload,
            record.Status,
            record.PerformedOn,
            record.StartedAtUtc,
            record.FinishedAtUtc,
            record.TotalVolumeKg,
            record.Notes,
            exercises,
            record.CreatedAtUtc,
            record.UpdatedAtUtc);

        session.ApplySyncMetadata(
            record.Version,
            record.SyncVersion,
            record.UpdatedAtUtc,
            record.DeletedAtUtc);

        return session;
    }

    private static ExerciseLog ToDomain(ExerciseLogRecord record) => ExerciseLog.FromStorage(
        record.Id,
        record.Position,
        record.ExerciseId,
        record.ExerciseName,
        record.ExerciseCategory,
        record.Type,
        record.Notes,
        [.. record.Sets.OrderBy(set => set.Position).Select(ToDomain)]);

    private static SetLog ToDomain(SetLogRecord record) => SetLog.FromStorage(
        record.Id,
        record.Position,
        Weight.OfNullable(record.PlannedWeightKg),
        record.PlannedReps,
        RpeTarget.FromBounds(record.PlannedRpeMin, record.PlannedRpeMax),
        Weight.OfNullable(record.ActualWeightKg),
        record.ActualReps,
        Rpe.OfNullable(record.ActualRpe),
        record.Completed,
        record.Notes);

    public static WorkoutSessionRecord ToRecord(WorkoutSession session)
    {
        var record = new WorkoutSessionRecord
        {
            Id = session.Id,
            UserId = session.UserId,
            TrainingCycleId = session.TrainingCycleId,
            TrainingWeekId = session.TrainingWeekId,
            WorkoutTemplateId = session.WorkoutTemplateId,
            WeekNumber = session.WeekNumber,
            DayNumber = session.DayNumber,
            Title = session.Title,
            IsDeload = session.IsDeload,
            PerformedOn = session.PerformedOn,
            StartedAtUtc = session.StartedAtUtc,
            CreatedAtUtc = session.CreatedAtUtc,
        };

        Apply(session, record);

        return record;
    }

    /// <summary>
    /// Reconciles a live session's tree onto its stored rows.
    ///
    /// Matching happens on identifier, never on position: the phone owns these GUIDs, and a set
    /// that moved from third to second in the list is still the same set with the same history.
    /// Deleting and re-inserting the tree on every keystroke would churn the primary keys the
    /// client is holding.
    /// </summary>
    public static void Apply(WorkoutSession session, WorkoutSessionRecord record)
    {
        record.Status = session.Status;
        record.FinishedAtUtc = session.FinishedAtUtc;
        record.Notes = session.Notes;
        record.DeletedAtUtc = session.DeletedAtUtc;

        // Frozen only once the session is finished; while it is live the number is derived from
        // the sets on every read, so it cannot drift away from the log.
        record.TotalVolumeKg = session.Status.IsFinished() ? session.TotalVolume.Kilograms : null;

        Reconcile(session, record);
    }

    private static void Reconcile(WorkoutSession session, WorkoutSessionRecord record)
    {
        var existing = record.Exercises.ToDictionary(exercise => exercise.Id);
        var wanted = session.Exercises.Select(exercise => exercise.Id).ToHashSet();

        foreach (var stale in existing.Values.Where(exercise => !wanted.Contains(exercise.Id)).ToList())
        {
            record.Exercises.Remove(stale);
        }

        foreach (var exercise in session.Exercises)
        {
            if (existing.TryGetValue(exercise.Id, out var exerciseRecord))
            {
                ApplyExercise(exercise, exerciseRecord);
                continue;
            }

            record.Exercises.Add(ToRecord(record.Id, exercise));
        }
    }

    private static void ApplyExercise(ExerciseLog exercise, ExerciseLogRecord record)
    {
        record.Position = exercise.Position;
        record.ExerciseId = exercise.ExerciseId;
        record.ExerciseName = exercise.ExerciseName;
        record.ExerciseCategory = exercise.ExerciseCategory;
        record.Type = exercise.Type;
        record.Notes = exercise.Notes;

        var existing = record.Sets.ToDictionary(set => set.Id);
        var wanted = exercise.Sets.Select(set => set.Id).ToHashSet();

        foreach (var stale in existing.Values.Where(set => !wanted.Contains(set.Id)).ToList())
        {
            record.Sets.Remove(stale);
        }

        foreach (var set in exercise.Sets)
        {
            if (existing.TryGetValue(set.Id, out var setRecord))
            {
                ApplySet(set, setRecord);
                continue;
            }

            record.Sets.Add(ToRecord(record.Id, set));
        }
    }

    private static void ApplySet(SetLog set, SetLogRecord record)
    {
        record.Position = set.Position;

        // Planned columns are written once, when the row is created. Nothing here touches them:
        // "what was asked for" is history the moment the session starts.
        record.ActualWeightKg = set.ActualWeight?.Kilograms;
        record.ActualReps = set.ActualReps;
        record.ActualRpe = set.ActualRpe?.Value;
        record.Completed = set.Completed;
        record.Notes = set.Notes;
    }

    private static ExerciseLogRecord ToRecord(Guid sessionId, ExerciseLog exercise) => new()
    {
        Id = exercise.Id,
        WorkoutSessionId = sessionId,
        ExerciseId = exercise.ExerciseId,
        ExerciseName = exercise.ExerciseName,
        ExerciseCategory = exercise.ExerciseCategory,
        Type = exercise.Type,
        Position = exercise.Position,
        Notes = exercise.Notes,
        Sets = [.. exercise.Sets.Select(set => ToRecord(exercise.Id, set))],
    };

    private static SetLogRecord ToRecord(Guid exerciseLogId, SetLog set) => new()
    {
        Id = set.Id,
        ExerciseLogId = exerciseLogId,
        Position = set.Position,
        PlannedWeightKg = set.PlannedWeight?.Kilograms,
        PlannedReps = set.PlannedReps,
        PlannedRpeMin = set.PlannedRpe?.Min.Value,
        PlannedRpeMax = set.PlannedRpe?.Max.Value,
        ActualWeightKg = set.ActualWeight?.Kilograms,
        ActualReps = set.ActualReps,
        ActualRpe = set.ActualRpe?.Value,
        Completed = set.Completed,
        Notes = set.Notes,
    };
}
