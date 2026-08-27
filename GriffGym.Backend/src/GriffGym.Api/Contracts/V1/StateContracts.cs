namespace GriffGym.Api.Contracts.V1;

/// <summary>
/// Everything one lifter's installation is made of, in one read-only document.
///
/// This is the answer to "my phone is in a river". A fresh install signs in, asks once, and has
/// what it needs to rebuild its local database exactly: the planning numbers, every cycle with
/// the plan it was actually trained on, where the lifter is inside the current plan, every
/// logged session with its planned and actual sets, and the workout still open.
///
/// <c>SchemaVersion</c> describes this document, not the database. A client can refuse a
/// version it does not understand rather than restore something it half recognises.
/// <c>SyncVersion</c> is the cursor a future delta sync will resume from.
/// </summary>
public sealed record ApplicationStateResponse(
    int SchemaVersion,
    DateTimeOffset GeneratedAtUtc,
    long SyncVersion,
    UserResponse Profile,
    IReadOnlyList<ReferenceMaxResponse> ReferenceMaxes,
    IReadOnlyList<ExerciseResponse> Exercises,
    IReadOnlyList<CycleResponse> Cycles,
    Guid? CurrentCycleId,
    Guid? ActiveWorkoutId,
    IReadOnlyList<WorkoutResponse> Workouts);
