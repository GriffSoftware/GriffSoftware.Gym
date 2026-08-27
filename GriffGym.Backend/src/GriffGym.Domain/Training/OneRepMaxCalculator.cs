namespace GriffGym.Domain.Training;

/// <summary>
/// Epley throughout: <c>1RM = weight x (1 + reps / 30)</c>.
///
/// A true single is returned untouched rather than inflated, which is why this is a method and
/// not a formula written inline wherever an estimate is needed.
/// </summary>
public static class OneRepMaxCalculator
{
    public static Weight? Estimate(Weight? weight, int? reps)
    {
        if (weight is not { } load || reps is not { } repetitions || repetitions < 1 || load.IsZero)
        {
            return null;
        }

        return repetitions == 1
            ? load
            : Weight.Of(load.Kilograms * (1m + (repetitions / 30m)));
    }
}

/// <summary>
/// Tonnage: kilograms moved, weight times reps summed over completed sets.
///
/// Its own type rather than a bare decimal because "150 kg on the bar" and "4 500 kg moved
/// today" are different quantities that happen to share a unit, and mixing them up silently
/// is exactly the sort of bug a type system exists to prevent.
/// </summary>
public readonly record struct TrainingVolume
{
    private TrainingVolume(decimal kilograms) => Kilograms = kilograms;

    public decimal Kilograms { get; }

    public static TrainingVolume Zero { get; } = new(0m);

    public bool IsZero => Kilograms == 0m;

    public static TrainingVolume Of(decimal kilograms) =>
        new(decimal.Round(Math.Max(0m, kilograms), 2, MidpointRounding.AwayFromZero));

    public static TrainingVolume From(Weight weight, int reps) =>
        reps <= 0 ? Zero : Of(weight.Kilograms * reps);

    public static TrainingVolume operator +(TrainingVolume left, TrainingVolume right) =>
        Of(left.Kilograms + right.Kilograms);

    public override string ToString() =>
        Kilograms.ToString("0.##", System.Globalization.CultureInfo.InvariantCulture);
}
