using GriffGym.Api.Contracts.V1;
using GriffGym.Application.State;

namespace GriffGym.Api.Mapping;

internal static class StateMapping
{
    public static ApplicationStateResponse ToResponse(this UserApplicationState state) => new(
        state.SchemaVersion,
        state.GeneratedAtUtc,
        state.SyncVersion,
        state.Profile.ToResponse(),
        [.. state.ReferenceMaxes.Select(TrainingMapping.ToResponse)],
        [.. state.Exercises.Select(TrainingMapping.ToResponse)],
        [.. state.Cycles.Select(TrainingMapping.ToResponse)],
        state.CurrentCycleId,
        state.ActiveWorkoutId,
        [.. state.Workouts.Select(WorkoutMapping.ToResponse)]);
}
