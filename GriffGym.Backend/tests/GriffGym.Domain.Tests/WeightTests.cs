using GriffGym.Domain.Common;
using GriffGym.Domain.Training;

namespace GriffGym.Domain.Tests;

public sealed class WeightTests
{
    [Theory]
    [InlineData("117.5")]
    [InlineData("132.5")]
    [InlineData("152.5")]
    [InlineData("162.5")]
    [InlineData("192.5")]
    public void Keeps_half_kilogram_loads_exactly(string kilograms)
    {
        // Loads on this program move in 2.5 and 1.25 kg steps. If half kilograms do not survive
        // a round trip, the whole training log is wrong.
        var value = decimal.Parse(kilograms, System.Globalization.CultureInfo.InvariantCulture);

        Assert.Equal(value, Weight.Of(value).Kilograms);
    }

    [Fact]
    public void Rounds_to_two_decimals()
    {
        Assert.Equal(117.51m, Weight.Of(117.514m).Kilograms);
        Assert.Equal(117.52m, Weight.Of(117.515m).Kilograms);
    }

    [Fact]
    public void Rejects_a_negative_load()
    {
        var exception = Assert.Throws<DomainException>(() => Weight.Of(-0.5m));

        Assert.Contains("cannot be negative", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Rejects_an_implausible_load()
    {
        Assert.Throws<DomainException>(() => Weight.Of(Weight.MaxKilograms + 1m));
    }

    [Fact]
    public void Calculates_a_percentage_of_a_reference_max()
    {
        // 89.29% of a 210 kg squat is the sheet's opening top single.
        Assert.Equal(187.51m, Weight.Of(210m).Percentage(89.29m).Kilograms);
    }

    [Theory]
    [InlineData("192.5", "192.5")]
    [InlineData("150.00", "150")]
    [InlineData("117.50", "117.5")]
    public void Formats_without_trailing_zeros(string input, string expected)
    {
        var value = decimal.Parse(input, System.Globalization.CultureInfo.InvariantCulture);

        Assert.Equal(expected, Weight.Of(value).ToString());
    }

    [Fact]
    public void Compares_by_load()
    {
        Assert.True(Weight.Of(100m) < Weight.Of(102.5m));
        Assert.True(Weight.Of(102.5m) >= Weight.Of(102.5m));
    }

    [Fact]
    public void Treats_equal_loads_as_equal()
    {
        Assert.Equal(Weight.Of(140m), Weight.Of(140.00m));
    }
}

public sealed class RpeTests
{
    [Theory]
    [InlineData(1.0)]
    [InlineData(6.5)]
    [InlineData(8.5)]
    [InlineData(10.0)]
    public void Accepts_half_steps_in_range(decimal value) => Assert.Equal(value, Rpe.Of(value).Value);

    [Theory]
    [InlineData(0.5)]
    [InlineData(10.5)]
    [InlineData(-1)]
    public void Rejects_values_outside_one_to_ten(decimal value)
    {
        Assert.Throws<DomainException>(() => Rpe.Of(value));
        Assert.False(Rpe.IsValid(value));
    }

    [Fact]
    public void Rejects_a_value_between_half_steps()
    {
        // Deliberately not rounded to 7.5. A client sending 7.31 has a bug, and quietly storing
        // something else would hide it.
        Assert.Throws<DomainException>(() => Rpe.Of(7.31m));
    }

    [Fact]
    public void Formats_a_whole_number_without_a_decimal_point() =>
        Assert.Equal("8", Rpe.Of(8m).ToString());

    [Fact]
    public void Formats_a_half_step_with_one_decimal() =>
        Assert.Equal("8.5", Rpe.Of(8.5m).ToString());
}

public sealed class RpeTargetTests
{
    [Fact]
    public void An_exact_target_is_not_a_range()
    {
        var target = RpeTarget.Exact(8m);

        Assert.False(target.IsRange);
        Assert.Equal("8", target.ToString());
    }

    [Fact]
    public void A_range_reads_as_a_range()
    {
        var target = RpeTarget.Range(6m, 7m);

        Assert.True(target.IsRange);
        Assert.Equal("6-7", target.ToString());
    }

    [Fact]
    public void Rejects_an_inverted_range()
    {
        var exception = Assert.Throws<DomainException>(() => RpeTarget.Range(8m, 6m));

        Assert.Contains("inverted", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Reads_back_as_nothing_when_both_bounds_are_absent() =>
        Assert.Null(RpeTarget.FromBounds(null, null));

    [Fact]
    public void Refuses_to_invent_a_missing_bound()
    {
        // Half an RPE target is a corrupt row, and guessing the other half would bury it.
        Assert.Throws<DomainException>(() => RpeTarget.FromBounds(6m, null));
        Assert.Throws<DomainException>(() => RpeTarget.FromBounds(null, 7m));
    }
}

public sealed class OneRepMaxCalculatorTests
{
    [Fact]
    public void Returns_a_true_single_untouched()
    {
        // Epley would inflate a single by 1/30th. A single is already a one rep max.
        Assert.Equal(Weight.Of(200m), OneRepMaxCalculator.Estimate(Weight.Of(200m), 1));
    }

    [Fact]
    public void Estimates_with_epley()
    {
        // 180 x (1 + 5/30) = 210
        Assert.Equal(Weight.Of(210m), OneRepMaxCalculator.Estimate(Weight.Of(180m), 5));
    }

    [Fact]
    public void Cannot_estimate_without_a_load() =>
        Assert.Null(OneRepMaxCalculator.Estimate(null, 5));

    [Fact]
    public void Cannot_estimate_without_a_rep_count() =>
        Assert.Null(OneRepMaxCalculator.Estimate(Weight.Of(100m), null));

    [Fact]
    public void Cannot_estimate_from_a_set_that_was_not_performed() =>
        Assert.Null(OneRepMaxCalculator.Estimate(Weight.Of(100m), 0));

    [Fact]
    public void Cannot_estimate_from_an_empty_bar() =>
        Assert.Null(OneRepMaxCalculator.Estimate(Weight.Zero, 5));
}

public sealed class TrainingVolumeTests
{
    [Fact]
    public void Multiplies_load_by_reps() =>
        Assert.Equal(600m, TrainingVolume.From(Weight.Of(120m), 5).Kilograms);

    [Fact]
    public void Is_zero_for_a_set_that_was_not_performed() =>
        Assert.True(TrainingVolume.From(Weight.Of(120m), 0).IsZero);

    [Fact]
    public void Adds_up() =>
        Assert.Equal(
            1200m,
            (TrainingVolume.From(Weight.Of(120m), 5) + TrainingVolume.From(Weight.Of(120m), 5))
            .Kilograms);
}
