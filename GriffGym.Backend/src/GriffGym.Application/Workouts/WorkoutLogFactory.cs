using GriffGym.Application.Abstractions;
using GriffGym.Domain.Training;
using GriffGym.Domain.Workouts;

namespace GriffGym.Application.Workouts;

/// <summary>
/// Turns the flat shapes a client sends into the logged tree the domain works with.
///
/// Shared by creation and by the offline upload path, because "an exercise log built from a
/// request" must mean exactly one thing however it arrives.
/// </summary>
internal sealed class WorkoutLogFactory(IIdentifierFactory identifiers)
{
    public IReadOnlyList<ExerciseLog> Build(IReadOnlyList<ExerciseLogInput> inputs) =>
        [.. inputs.Select(Build)];

    public ExerciseLog Build(ExerciseLogInput input) => ExerciseLog.Create(
        input.Id ?? identifiers.NewId(),
        input.Position,
        input.ExerciseId,
        input.ExerciseName ?? "Unknown exercise",
        input.ExerciseCategory ?? ExerciseCategory.Accessory,
        input.Type,
        input.Notes,
        [.. input.Sets.Select(Build)]);

    public SetLog Build(SetLogInput input) => SetLog.Create(
        input.Id ?? identifiers.NewId(),
        input.Position,
        Weight.OfNullable(input.PlannedWeightKg),
        input.PlannedReps,
        RpeTarget.FromBounds(input.PlannedRpeMin, input.PlannedRpeMax),
        new SetResult(
            Weight.OfNullable(input.ActualWeightKg),
            input.ActualReps,
            Rpe.OfNullable(input.ActualRpe),
            input.Completed,
            input.Notes));
}
