using GriffGym.Domain.Common;
using GriffGym.Domain.Training;

namespace GriffGym.Domain.Workouts;

/// <summary>
/// One movement performed inside a session, with every set logged against it.
///
/// The name and category are snapshots. History must not depend on the current contents of the
/// exercise catalogue: renaming "Ławka" to "Bench" three cycles from now cannot be allowed to
/// rewrite what was trained last March.
/// </summary>
public sealed class ExerciseLog
{
    public const int MaxNotesLength = 500;

    private ExerciseLog(
        Guid id,
        int position,
        Guid? exerciseId,
        string exerciseName,
        ExerciseCategory exerciseCategory,
        ExerciseType type,
        string? notes,
        List<SetLog> sets)
    {
        DomainException.Require(id != Guid.Empty, "An exercise log needs a non-empty identifier.");
        DomainException.Require(position >= 1, $"Exercise positions start at one, got {position}.");
        DomainException.Require(
            !string.IsNullOrWhiteSpace(exerciseName),
            "An exercise log must snapshot the exercise name.");
        DomainException.Require(
            sets.Select(set => set.Position).Distinct().Count() == sets.Count,
            $"'{exerciseName}' has two sets at the same position.");
        DomainException.Require(
            sets.Select(set => set.Id).Distinct().Count() == sets.Count,
            $"'{exerciseName}' has two sets with the same identifier.");

        Id = id;
        Position = position;
        ExerciseId = exerciseId;
        ExerciseName = exerciseName.Trim();
        ExerciseCategory = exerciseCategory;
        Type = type;
        Notes = TrimNotes(notes);
        _sets = [.. sets.OrderBy(set => set.Position)];
    }

    private readonly List<SetLog> _sets;

    public Guid Id { get; }

    public int Position { get; }

    /// <summary>
    /// Provenance only, and nullable on purpose: an exercise removed from the catalogue must
    /// not take the log of what was trained with it.
    /// </summary>
    public Guid? ExerciseId { get; }

    public string ExerciseName { get; }

    public ExerciseCategory ExerciseCategory { get; }

    public ExerciseType Type { get; }

    public string? Notes { get; private set; }

    public IReadOnlyList<SetLog> Sets => _sets;

    public bool IsMainLift => Type.IsMainLift();

    public TrainingVolume Volume =>
        _sets.Aggregate(TrainingVolume.Zero, (total, set) => total + set.Volume);

    /// <summary>Best Epley estimate this movement produced, ignoring unfinished sets.</summary>
    public Weight? BestEstimatedOneRepMax =>
        _sets.Select(set => set.EstimatedOneRepMax)
            .Where(estimate => estimate is not null)
            .OrderByDescending(estimate => estimate!.Value.Kilograms)
            .FirstOrDefault();

    public static ExerciseLog Create(
        Guid id,
        int position,
        Guid? exerciseId,
        string exerciseName,
        ExerciseCategory exerciseCategory,
        ExerciseType type,
        string? notes,
        IReadOnlyList<SetLog> sets) =>
        new(id, position, exerciseId, exerciseName, exerciseCategory, type, notes, [.. sets]);

    public static ExerciseLog FromStorage(
        Guid id,
        int position,
        Guid? exerciseId,
        string exerciseName,
        ExerciseCategory exerciseCategory,
        ExerciseType type,
        string? notes,
        IReadOnlyList<SetLog> sets) =>
        new(id, position, exerciseId, exerciseName, exerciseCategory, type, notes, [.. sets]);

    /// <summary>
    /// Snapshots a planned movement into a log the lifter can start writing results into.
    /// Every prescribed set becomes its own row carrying what was asked for.
    /// </summary>
    public static ExerciseLog FromTemplate(
        ExerciseTemplate template,
        Func<Guid> newExerciseLogId,
        Func<Guid> newSetLogId)
    {
        var sets = template.PlannedSets
            .Select(planned => SetLog.Planned(
                newSetLogId(),
                planned.Position,
                planned.Weight,
                planned.Reps,
                planned.TargetRpe))
            .ToList();

        return new ExerciseLog(
            newExerciseLogId(),
            template.Position,
            template.ExerciseId,
            template.ExerciseName,
            template.ExerciseCategory,
            template.Type,
            notes: null,
            sets);
    }

    internal SetLog? FindSet(Guid setLogId) => _sets.FirstOrDefault(set => set.Id == setLogId);

    internal void UpdateNotes(string? notes) => Notes = TrimNotes(notes);

    private static string? TrimNotes(string? notes)
    {
        if (string.IsNullOrWhiteSpace(notes))
        {
            return null;
        }

        var trimmed = notes.Trim();
        return trimmed.Length <= MaxNotesLength ? trimmed : trimmed[..MaxNotesLength];
    }
}
