using GriffGym.Domain.Common;

namespace GriffGym.Domain.Training;

/// <summary>
/// One six-week run through the block: the aggregate the whole training model hangs from.
///
/// A cycle owns exactly one generated <see cref="TrainingProgram"/> and the maxes that program
/// was built from. Cycles are numbered from one and never renumbered, so "cycle 3" means the
/// same thing forever, and a completed cycle is immutable history in exactly the way a
/// finished session is.
///
/// The snapshot is fixed at creation. Changing a reference max later is a normal, supported
/// thing to do and it deliberately does not reach back into a cycle that was already planned.
/// </summary>
public sealed class TrainingCycle : Entity
{
    private TrainingCycle(
        Guid id,
        Guid userId,
        int cycleNumber,
        TrainingCycleStatus status,
        ReferenceMaxSnapshot referenceMaxes,
        DateTimeOffset startedAtUtc,
        DateTimeOffset? completedAtUtc,
        TrainingProgram program,
        DateTimeOffset createdAtUtc,
        DateTimeOffset updatedAtUtc)
        : base(id, createdAtUtc, updatedAtUtc)
    {
        DomainException.Require(userId != Guid.Empty, "A training cycle must belong to a user.");
        DomainException.Require(cycleNumber >= 1, $"Cycles are numbered from one, got {cycleNumber}.");
        DomainException.Require(
            (status == TrainingCycleStatus.Completed) == (completedAtUtc is not null),
            $"A cycle is completed exactly when it has a completion time, got {status} / {completedAtUtc}.");
        DomainException.Require(
            completedAtUtc is null || completedAtUtc >= startedAtUtc,
            "A cycle cannot be completed before it started.");

        UserId = userId;
        CycleNumber = cycleNumber;
        Status = status;
        ReferenceMaxes = referenceMaxes;
        StartedAtUtc = startedAtUtc;
        CompletedAtUtc = completedAtUtc;
        Program = program;
    }

    public Guid UserId { get; }

    public int CycleNumber { get; }

    public TrainingCycleStatus Status { get; private set; }

    /// <summary>Immutable once the cycle exists. That is the point of a snapshot.</summary>
    public ReferenceMaxSnapshot ReferenceMaxes { get; }

    public DateTimeOffset StartedAtUtc { get; }

    public DateTimeOffset? CompletedAtUtc { get; private set; }

    public TrainingProgram Program { get; }

    public bool IsActive => Status == TrainingCycleStatus.Active;

    public bool IsCompleted => Status == TrainingCycleStatus.Completed;

    /// <summary>"CYCLE 3" — the label the whole app refers to a cycle by.</summary>
    public string Label => $"CYCLE {CycleNumber}";

    public static TrainingCycle Start(
        Guid id,
        Guid userId,
        int cycleNumber,
        ReferenceMaxSnapshot referenceMaxes,
        TrainingProgram program,
        DateTimeOffset startedAtUtc,
        DateTimeOffset now) =>
        new(
            id,
            userId,
            cycleNumber,
            TrainingCycleStatus.Active,
            referenceMaxes,
            startedAtUtc,
            completedAtUtc: null,
            program,
            now,
            now);

    public static TrainingCycle FromStorage(
        Guid id,
        Guid userId,
        int cycleNumber,
        TrainingCycleStatus status,
        ReferenceMaxSnapshot referenceMaxes,
        DateTimeOffset startedAtUtc,
        DateTimeOffset? completedAtUtc,
        TrainingProgram program,
        DateTimeOffset createdAtUtc,
        DateTimeOffset updatedAtUtc) =>
        new(
            id,
            userId,
            cycleNumber,
            status,
            referenceMaxes,
            startedAtUtc,
            completedAtUtc,
            program,
            createdAtUtc,
            updatedAtUtc);

    /// <summary>
    /// Closes the cycle and clears its progress pointer in the same breath — "there is no next
    /// workout" and "the cycle is finished" are one fact, and letting them be set separately
    /// would let them disagree.
    /// </summary>
    public void Complete(DateTimeOffset completedAtUtc, DateTimeOffset now)
    {
        DomainException.Require(IsActive, $"{Label} is already completed.");
        DomainException.Require(
            completedAtUtc >= StartedAtUtc,
            "A cycle cannot be completed before it started.");

        Status = TrainingCycleStatus.Completed;
        CompletedAtUtc = completedAtUtc;
        Program.MoveProgressTo(null);
        Touch(now);
    }

    /// <summary>
    /// Points the plan at the next unit to train. Null means the program has run out, which
    /// only a completed cycle is allowed to say.
    /// </summary>
    public void MoveProgressTo(Guid? workoutTemplateId, DateTimeOffset now)
    {
        DomainException.Require(
            IsActive,
            $"{Label} is completed; its progress can no longer be moved.");

        if (Program.CurrentWorkoutTemplateId == workoutTemplateId)
        {
            return;
        }

        Program.MoveProgressTo(workoutTemplateId);
        Touch(now);
    }
}
