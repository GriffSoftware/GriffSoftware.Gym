using GriffGym.Domain.Common;

namespace GriffGym.Domain.Training;

/// <summary>
/// The lifter's declared current maximum for one of the big three.
///
/// A planning number, deliberately not a personal record: it was typed in, not performed.
/// This is what the lifter believes their max is *today* and is theirs to edit at any point;
/// what a particular block was calculated from is frozen separately in
/// <see cref="ReferenceMaxSnapshot"/> and never changes again.
/// </summary>
public sealed class ReferenceMax : Entity
{
    private ReferenceMax(
        Guid id,
        Guid userId,
        LiftType lift,
        Weight value,
        DateTimeOffset createdAtUtc,
        DateTimeOffset updatedAtUtc)
        : base(id, createdAtUtc, updatedAtUtc)
    {
        DomainException.Require(userId != Guid.Empty, "A reference max must belong to a user.");

        UserId = userId;
        Lift = lift;
        Value = value;
    }

    public Guid UserId { get; }

    public LiftType Lift { get; }

    public Weight Value { get; private set; }

    public static ReferenceMax Create(
        Guid id,
        Guid userId,
        LiftType lift,
        Weight value,
        DateTimeOffset now) =>
        new(id, userId, lift, value, now, now);

    public static ReferenceMax FromStorage(
        Guid id,
        Guid userId,
        LiftType lift,
        Weight value,
        DateTimeOffset createdAtUtc,
        DateTimeOffset updatedAtUtc) =>
        new(id, userId, lift, value, createdAtUtc, updatedAtUtc);

    /// <summary>
    /// Changes the planning number. Historical cycles keep the snapshot they were built from —
    /// that is the whole reason the two live in different tables.
    /// </summary>
    public void UpdateValue(Weight value, DateTimeOffset now)
    {
        if (Value == value)
        {
            return;
        }

        Value = value;
        Touch(now);
    }
}

/// <summary>
/// The three planning numbers a cycle was generated from, frozen when it started.
///
/// Stored as three columns rather than a serialised blob: they are three numbers with three
/// fixed meanings, and "what was cycle 4 built on?" should be answerable without decoding
/// anything.
/// </summary>
public readonly record struct ReferenceMaxSnapshot
{
    private ReferenceMaxSnapshot(Weight squat, Weight benchPress, Weight deadlift)
    {
        Squat = squat;
        BenchPress = benchPress;
        Deadlift = deadlift;
    }

    public Weight Squat { get; }

    public Weight BenchPress { get; }

    public Weight Deadlift { get; }

    public Weight this[LiftType lift] => lift switch
    {
        LiftType.Squat => Squat,
        LiftType.BenchPress => BenchPress,
        LiftType.Deadlift => Deadlift,
        _ => throw new ArgumentOutOfRangeException(nameof(lift), lift, "Unknown lift."),
    };

    /// <summary>
    /// A cycle cannot be planned from a zero max, so creating one rejects it outright.
    /// <see cref="FromStorage"/> stays permissive, because refusing to read a row that is
    /// already on disk would be worse than showing it honestly.
    /// </summary>
    public static ReferenceMaxSnapshot Of(Weight squat, Weight benchPress, Weight deadlift)
    {
        DomainException.Require(!squat.IsZero, "A cycle needs a squat reference max above zero.");
        DomainException.Require(!benchPress.IsZero, "A cycle needs a bench press reference max above zero.");
        DomainException.Require(!deadlift.IsZero, "A cycle needs a deadlift reference max above zero.");

        return new ReferenceMaxSnapshot(squat, benchPress, deadlift);
    }

    public static ReferenceMaxSnapshot FromStorage(Weight squat, Weight benchPress, Weight deadlift) =>
        new(squat, benchPress, deadlift);
}
