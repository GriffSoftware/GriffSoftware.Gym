using GriffGym.Domain.Common;

namespace GriffGym.Domain.Training;

/// <summary>
/// A training load in kilograms.
///
/// Loads on this program move in 2.5 kg and 1.25 kg steps, so half and quarter kilograms
/// (117.5, 132.5, 152.5, 162.5) are first-class values — a weight is never an integer, and
/// never a <see cref="double"/> either. It is a <see cref="decimal"/> so that a load written
/// to PostgreSQL, read back and compared is bit-for-bit the number the lifter typed.
/// </summary>
public readonly record struct Weight : IComparable<Weight>
{
    /// <summary>Matches the numeric(7,2) column the value is stored in.</summary>
    public const int Scale = 2;

    public const decimal MaxKilograms = 99999.99m;

    private Weight(decimal kilograms) => Kilograms = kilograms;

    public decimal Kilograms { get; }

    public static Weight Zero { get; } = new(0m);

    public bool IsZero => Kilograms == 0m;

    public static Weight Of(decimal kilograms)
    {
        DomainException.Require(kilograms >= 0m, $"Weight cannot be negative, was {kilograms}.");
        DomainException.Require(
            kilograms <= MaxKilograms,
            $"Weight must be at most {MaxKilograms} kg, was {kilograms}.");

        return new Weight(decimal.Round(kilograms, Scale, MidpointRounding.AwayFromZero));
    }

    public static Weight? OfNullable(decimal? kilograms) => kilograms is null ? null : Of(kilograms.Value);

    public static bool IsValid(decimal kilograms) => kilograms >= 0m && kilograms <= MaxKilograms;

    /// <summary>A share of a reference max, as the training template prescribes it.</summary>
    public Weight Percentage(decimal percent) => Of(Kilograms * percent / 100m);

    public static Weight operator +(Weight left, Weight right) => Of(left.Kilograms + right.Kilograms);

    public static Weight operator *(Weight weight, int factor) => Of(weight.Kilograms * factor);

    public int CompareTo(Weight other) => Kilograms.CompareTo(other.Kilograms);

    public static bool operator <(Weight left, Weight right) => left.CompareTo(right) < 0;

    public static bool operator >(Weight left, Weight right) => left.CompareTo(right) > 0;

    public static bool operator <=(Weight left, Weight right) => left.CompareTo(right) <= 0;

    public static bool operator >=(Weight left, Weight right) => left.CompareTo(right) >= 0;

    /// <summary>"192.5", "150" — trailing zeros dropped, as the app displays them.</summary>
    public override string ToString() =>
        Kilograms == decimal.Truncate(Kilograms)
            ? decimal.Truncate(Kilograms).ToString(System.Globalization.CultureInfo.InvariantCulture)
            : Kilograms.ToString("0.##", System.Globalization.CultureInfo.InvariantCulture);
}
