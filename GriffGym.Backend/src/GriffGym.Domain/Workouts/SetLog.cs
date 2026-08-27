using GriffGym.Domain.Common;
using GriffGym.Domain.Training;

namespace GriffGym.Domain.Workouts;

/// <summary>The values a lifter actually enters for one set.</summary>
public readonly record struct SetResult(
    Weight? Weight,
    int? Reps,
    Rpe? Rpe,
    bool Completed,
    string? Notes)
{
    /// <summary>
    /// A set cannot be ticked off without saying what was lifted. Rejecting this here keeps
    /// "completed" from ever meaning "completed, contents unknown" in the history.
    /// </summary>
    public void Validate()
    {
        if (!Completed)
        {
            return;
        }

        DomainException.Require(
            Weight is not null && Reps is not null,
            "A completed set must record both a weight and a rep count.");
        DomainException.Require(Reps >= 1, $"A completed set cannot record {Reps} reps.");
    }
}

/// <summary>
/// One logged set: what was asked for, next to what happened.
///
/// The planned columns are a snapshot taken when the session started. Editing the program
/// later can never rewrite them, and nothing in this class lets an actual value overwrite a
/// planned one — they are separate state, not two views of the same field.
/// </summary>
public sealed class SetLog
{
    public const int MaxNotesLength = 500;

    private SetLog(
        Guid id,
        int position,
        Weight? plannedWeight,
        int? plannedReps,
        RpeTarget? plannedRpe,
        Weight? actualWeight,
        int? actualReps,
        Rpe? actualRpe,
        bool completed,
        string? notes)
    {
        DomainException.Require(id != Guid.Empty, "A set log needs a non-empty identifier.");
        DomainException.Require(position >= 1, $"Set positions start at one, got {position}.");
        DomainException.Require(plannedReps is null or >= 1, $"A set cannot plan {plannedReps} reps.");
        DomainException.Require(actualReps is null or >= 0, $"A set cannot record {actualReps} reps.");

        Id = id;
        Position = position;
        PlannedWeight = plannedWeight;
        PlannedReps = plannedReps;
        PlannedRpe = plannedRpe;
        ActualWeight = actualWeight;
        ActualReps = actualReps;
        ActualRpe = actualRpe;
        Completed = completed;
        Notes = TrimNotes(notes);
    }

    public Guid Id { get; }

    public int Position { get; }

    public Weight? PlannedWeight { get; }

    public int? PlannedReps { get; }

    public RpeTarget? PlannedRpe { get; }

    public Weight? ActualWeight { get; private set; }

    public int? ActualReps { get; private set; }

    public Rpe? ActualRpe { get; private set; }

    public bool Completed { get; private set; }

    public string? Notes { get; private set; }

    public TrainingVolume Volume =>
        Completed && ActualWeight is { } weight && ActualReps is { } reps
            ? TrainingVolume.From(weight, reps)
            : TrainingVolume.Zero;

    public Weight? EstimatedOneRepMax =>
        Completed ? OneRepMaxCalculator.Estimate(ActualWeight, ActualReps) : null;

    /// <summary>A set as the plan prescribes it, before anything has been lifted.</summary>
    public static SetLog Planned(
        Guid id,
        int position,
        Weight? plannedWeight,
        int? plannedReps,
        RpeTarget? plannedRpe) =>
        new(id, position, plannedWeight, plannedReps, plannedRpe, null, null, null, false, null);

    public static SetLog Create(
        Guid id,
        int position,
        Weight? plannedWeight,
        int? plannedReps,
        RpeTarget? plannedRpe,
        SetResult result)
    {
        result.Validate();

        return new SetLog(
            id,
            position,
            plannedWeight,
            plannedReps,
            plannedRpe,
            result.Weight,
            result.Reps,
            result.Rpe,
            result.Completed,
            result.Notes);
    }

    public static SetLog FromStorage(
        Guid id,
        int position,
        Weight? plannedWeight,
        int? plannedReps,
        RpeTarget? plannedRpe,
        Weight? actualWeight,
        int? actualReps,
        Rpe? actualRpe,
        bool completed,
        string? notes) =>
        new(
            id,
            position,
            plannedWeight,
            plannedReps,
            plannedRpe,
            actualWeight,
            actualReps,
            actualRpe,
            completed,
            notes);

    internal void Record(SetResult result)
    {
        result.Validate();

        ActualWeight = result.Weight;
        ActualReps = result.Reps;
        ActualRpe = result.Rpe;
        Completed = result.Completed;
        Notes = TrimNotes(result.Notes);
    }

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
