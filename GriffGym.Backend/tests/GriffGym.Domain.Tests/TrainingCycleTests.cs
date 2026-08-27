using GriffGym.Domain.Common;
using GriffGym.Domain.Training;

namespace GriffGym.Domain.Tests;

public sealed class ReferenceMaxSnapshotTests
{
    [Fact]
    public void Refuses_to_plan_a_cycle_from_a_zero_max()
    {
        // Nothing sensible can be generated from a zero, so it is refused where a cycle is
        // created rather than producing a plan of empty bars.
        var exception = Assert.Throws<DomainException>(() => ReferenceMaxSnapshot.Of(
            Weight.Zero,
            Weight.Of(170m),
            Weight.Of(225m)));

        Assert.Contains("squat", exception.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void Reads_a_stored_zero_back_honestly()
    {
        // An installation upgraded from before cycles existed can hold one. Refusing to read
        // the row would be worse than showing it as it is.
        var snapshot = ReferenceMaxSnapshot.FromStorage(Weight.Zero, Weight.Of(170m), Weight.Of(225m));

        Assert.True(snapshot.Squat.IsZero);
    }

    [Fact]
    public void Answers_by_lift()
    {
        var snapshot = TrainingFixtures.Snapshot();

        Assert.Equal(Weight.Of(210m), snapshot[LiftType.Squat]);
        Assert.Equal(Weight.Of(170m), snapshot[LiftType.BenchPress]);
        Assert.Equal(Weight.Of(225m), snapshot[LiftType.Deadlift]);
    }
}

public sealed class TrainingProgramTests
{
    [Fact]
    public void Orders_units_as_a_sequence_not_a_calendar()
    {
        var program = TrainingFixtures.Program();

        var order = program.Workouts.Select(workout => workout.SequenceNumber).ToList();

        Assert.Equal(18, order.Count);
        Assert.Equal(Enumerable.Range(1, 18), order);
    }

    [Fact]
    public void Rejects_two_weeks_with_the_same_number()
    {
        var week = new TrainingWeek(
            Guid.NewGuid(),
            1,
            "ACCUMULATION",
            TrainingWeekType.Training,
            [TrainingFixtures.Workout(1, 1, 1, isDeload: false)]);

        var duplicate = new TrainingWeek(
            Guid.NewGuid(),
            1,
            "ACCUMULATION",
            TrainingWeekType.Training,
            [TrainingFixtures.Workout(1, 1, 2, isDeload: false)]);

        Assert.Throws<DomainException>(() =>
            new TrainingProgram(Guid.NewGuid(), "Block", [week, duplicate], null));
    }

    [Fact]
    public void Rejects_a_progress_pointer_at_a_workout_it_does_not_own()
    {
        var exception = Assert.Throws<DomainException>(() =>
            TrainingFixtures.Program(currentWorkoutTemplateId: Guid.NewGuid()));

        Assert.Contains("inside this program", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Marks_week_six_as_the_deload()
    {
        var program = TrainingFixtures.Program();

        Assert.All(program.Weeks.Where(week => week.WeekNumber < 6), week => Assert.False(week.IsDeload));
        Assert.True(program.Weeks.Single(week => week.WeekNumber == 6).IsDeload);
    }
}

public sealed class TrainingCycleTests
{
    [Fact]
    public void Starts_active_and_unfinished()
    {
        var cycle = TrainingFixtures.Cycle();

        Assert.True(cycle.IsActive);
        Assert.Null(cycle.CompletedAtUtc);
        Assert.Equal("CYCLE 1", cycle.Label);
    }

    [Fact]
    public void Rejects_a_cycle_number_below_one()
    {
        Assert.Throws<DomainException>(() => TrainingFixtures.Cycle(cycleNumber: 0));
    }

    [Fact]
    public void Completing_clears_the_progress_pointer()
    {
        // "There is no next workout" and "the cycle is finished" are one fact. Letting them be
        // set separately would let them disagree.
        var cycle = TrainingFixtures.Cycle();
        var firstUnit = cycle.Program.Workouts.First().Id;

        cycle.MoveProgressTo(firstUnit, TrainingFixtures.Now);
        Assert.Equal(firstUnit, cycle.Program.CurrentWorkoutTemplateId);

        cycle.Complete(TrainingFixtures.Now.AddDays(42), TrainingFixtures.Now.AddDays(42));

        Assert.True(cycle.IsCompleted);
        Assert.Null(cycle.Program.CurrentWorkoutTemplateId);
    }

    [Fact]
    public void Cannot_be_completed_twice()
    {
        var cycle = TrainingFixtures.Cycle();
        cycle.Complete(TrainingFixtures.Now.AddDays(42), TrainingFixtures.Now.AddDays(42));

        var exception = Assert.Throws<DomainException>(() =>
            cycle.Complete(TrainingFixtures.Now.AddDays(43), TrainingFixtures.Now.AddDays(43)));

        Assert.Contains("already completed", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Cannot_be_completed_before_it_started()
    {
        var cycle = TrainingFixtures.Cycle();

        Assert.Throws<DomainException>(() =>
            cycle.Complete(TrainingFixtures.Now.AddDays(-1), TrainingFixtures.Now));
    }

    [Fact]
    public void A_completed_cycle_stops_accepting_progress()
    {
        var cycle = TrainingFixtures.Cycle();
        var unit = cycle.Program.Workouts.First().Id;
        cycle.Complete(TrainingFixtures.Now.AddDays(42), TrainingFixtures.Now.AddDays(42));

        Assert.Throws<DomainException>(() => cycle.MoveProgressTo(unit, TrainingFixtures.Now));
    }

    [Fact]
    public void The_snapshot_is_fixed_once_the_cycle_exists()
    {
        // There is deliberately no setter to test against: changing a lifter's reference max
        // later must not reach back into a block that was already planned.
        var cycle = TrainingFixtures.Cycle();

        Assert.Equal(Weight.Of(210m), cycle.ReferenceMaxes.Squat);
        Assert.Null(typeof(TrainingCycle).GetProperty(nameof(TrainingCycle.ReferenceMaxes))!.SetMethod);
    }
}
