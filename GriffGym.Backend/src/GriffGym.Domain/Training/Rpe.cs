using GriffGym.Domain.Common;

namespace GriffGym.Domain.Training;

/// <summary>
/// Rate of Perceived Exertion: the subjective intensity of one set, 1.0 to 10.0 in half steps.
///
/// The half-step rule is enforced rather than rounded silently. A client that sends 7.31 has
/// a bug, and quietly storing 7.5 would hide it.
/// </summary>
public readonly record struct Rpe : IComparable<Rpe>
{
    public const decimal MinValue = 1.0m;
    public const decimal MaxValue = 10.0m;
    public const decimal Step = 0.5m;

    private Rpe(decimal value) => Value = value;

    public decimal Value { get; }

    public static Rpe Min { get; } = new(MinValue);

    public static Rpe Max { get; } = new(MaxValue);

    public static Rpe Of(decimal value)
    {
        DomainException.Require(
            IsValid(value),
            $"RPE must be within {MinValue}..{MaxValue} in steps of {Step}, was {value}.");

        return new Rpe(decimal.Round(value, 1, MidpointRounding.AwayFromZero));
    }

    public static Rpe? OfNullable(decimal? value) => value is null ? null : Of(value.Value);

    public static bool IsValid(decimal value) =>
        value >= MinValue && value <= MaxValue && value % Step == 0m;

    public int CompareTo(Rpe other) => Value.CompareTo(other.Value);

    public static bool operator <(Rpe left, Rpe right) => left.CompareTo(right) < 0;

    public static bool operator >(Rpe left, Rpe right) => left.CompareTo(right) > 0;

    public static bool operator <=(Rpe left, Rpe right) => left.CompareTo(right) <= 0;

    public static bool operator >=(Rpe left, Rpe right) => left.CompareTo(right) >= 0;

    public override string ToString() =>
        Value == decimal.Truncate(Value)
            ? decimal.Truncate(Value).ToString(System.Globalization.CultureInfo.InvariantCulture)
            : Value.ToString("0.#", System.Globalization.CultureInfo.InvariantCulture);
}

/// <summary>
/// A planned intensity: either exact ("RPE 8") or a range ("RPE 6-7"), as accessory work is
/// prescribed. Stored as two columns so a range never has to be parsed back out of a string.
/// </summary>
public readonly record struct RpeTarget
{
    private RpeTarget(Rpe min, Rpe max)
    {
        Min = min;
        Max = max;
    }

    public Rpe Min { get; }

    public Rpe Max { get; }

    public bool IsRange => Min != Max;

    public static RpeTarget Exact(decimal value) => Of(Rpe.Of(value), Rpe.Of(value));

    public static RpeTarget Range(decimal min, decimal max) => Of(Rpe.Of(min), Rpe.Of(max));

    public static RpeTarget Of(Rpe min, Rpe max)
    {
        DomainException.Require(min <= max, $"RPE target range is inverted: {min} > {max}.");
        return new RpeTarget(min, max);
    }

    /// <summary>
    /// Rebuilds a target from two nullable columns. Both null means "no target"; exactly one
    /// null is a corrupt row and says so rather than inventing the missing bound.
    /// </summary>
    public static RpeTarget? FromBounds(decimal? min, decimal? max)
    {
        if (min is null && max is null)
        {
            return null;
        }

        DomainException.Require(
            min is not null && max is not null,
            "An RPE target needs both bounds or neither.");

        return Of(Rpe.Of(min!.Value), Rpe.Of(max!.Value));
    }

    public override string ToString() => IsRange ? $"{Min}-{Max}" : Min.ToString();
}
