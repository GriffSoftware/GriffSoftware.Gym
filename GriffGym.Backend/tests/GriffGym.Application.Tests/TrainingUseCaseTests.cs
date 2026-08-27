using GriffGym.Application.Common;
using GriffGym.Application.Cycles;
using GriffGym.Application.ReferenceMaxes;
using GriffGym.Application.Workouts;
using GriffGym.Domain.Training;

namespace GriffGym.Application.Tests;

public sealed class CreateTrainingCycleUseCaseTests
{
    [Fact]
    public async Task Persists_the_whole_six_week_plan()
    {
        var harness = new TrainingHarness();

        var result = await harness.CreateCycle.ExecuteAsync(TrainingHarness.SixWeekBlock(), default);

        Assert.True(result.WasCreated);
        Assert.Equal(6, result.Cycle.Program.Weeks.Count);
        Assert.Equal(18, result.Cycle.Program.Weeks.Sum(week => week.Workouts.Count));
        Assert.Equal(
            36,
            result.Cycle.Program.Weeks
                .SelectMany(week => week.Workouts)
                .SelectMany(workout => workout.Exercises)
                .Sum(exercise => exercise.PlannedSets.Count));
    }

    [Fact]
    public async Task Freezes_the_reference_maxes_the_plan_was_built_from()
    {
        var harness = new TrainingHarness();

        var result = await harness.CreateCycle.ExecuteAsync(TrainingHarness.SixWeekBlock(), default);

        Assert.Equal(210m, result.Cycle.ReferenceMaxes.SquatKg);
        Assert.Equal(170m, result.Cycle.ReferenceMaxes.BenchPressKg);
        Assert.Equal(225m, result.Cycle.ReferenceMaxes.DeadliftKg);
    }

    [Fact]
    public async Task Week_six_is_a_deload_at_half_the_reference_max()
    {
        var harness = new TrainingHarness();

        var result = await harness.CreateCycle.ExecuteAsync(TrainingHarness.SixWeekBlock(), default);

        var deload = result.Cycle.Program.Weeks.Single(week => week.WeekNumber == 6);

        Assert.True(deload.IsDeload);
        Assert.Equal(TrainingWeekType.Deload, deload.Type);
        Assert.Equal(105m, deload.Workouts[0].Exercises[0].PlannedSets[0].WeightKg);
    }

    [Fact]
    public async Task Points_a_new_cycle_at_its_first_unit()
    {
        var harness = new TrainingHarness();

        var result = await harness.CreateCycle.ExecuteAsync(TrainingHarness.SixWeekBlock(), default);

        var first = result.Cycle.Program.Weeks[0].Workouts[0];
        Assert.Equal(first.Id, result.Cycle.Program.CurrentWorkoutTemplateId);
    }

    [Fact]
    public async Task Adds_the_movements_the_plan_refers_to()
    {
        // The plan carries its own exercises, so persisting it never depends on a catalogue
        // having been seeded first.
        var harness = new TrainingHarness();

        await harness.CreateCycle.ExecuteAsync(TrainingHarness.SixWeekBlock(), default);

        var catalogue = await harness.Exercises.ListForUserAsync(harness.UserId, default);
        Assert.Equal(2, catalogue.Count);
        Assert.Contains(catalogue, exercise => exercise.Name == "Przysiad");
    }

    [Fact]
    public async Task Snapshots_the_exercise_name_onto_the_template()
    {
        var harness = new TrainingHarness();

        var result = await harness.CreateCycle.ExecuteAsync(TrainingHarness.SixWeekBlock(), default);

        var template = result.Cycle.Program.Weeks[0].Workouts[0].Exercises[0];
        Assert.Equal("Przysiad", template.ExerciseName);
        Assert.Equal(ExerciseCategory.Squat, template.ExerciseCategory);
    }

    [Fact]
    public async Task Sending_the_same_request_twice_creates_one_cycle()
    {
        // A phone that retried after a timeout is the normal case, not an error.
        var harness = new TrainingHarness();
        var cycleId = Guid.NewGuid();

        var first = await harness.CreateCycle.ExecuteAsync(
            TrainingHarness.SixWeekBlock(cycleId), default);
        var second = await harness.CreateCycle.ExecuteAsync(
            TrainingHarness.SixWeekBlock(cycleId), default);

        Assert.True(first.WasCreated);
        Assert.False(second.WasCreated);
        Assert.Equal(first.Cycle.Id, second.Cycle.Id);
        Assert.Single(await harness.Cycles.ListForUserAsync(harness.UserId, default));
    }

    [Fact]
    public async Task Refuses_a_cycle_number_the_lifter_already_used()
    {
        // Cycles are numbered once and never renumbered: "cycle 3" means the same thing forever.
        var harness = new TrainingHarness();
        await harness.CreateCycle.ExecuteAsync(TrainingHarness.SixWeekBlock(Guid.NewGuid()), default);

        await Assert.ThrowsAsync<ConflictException>(() => harness.CreateCycle.ExecuteAsync(
            TrainingHarness.SixWeekBlock(Guid.NewGuid()),
            default));
    }

    [Fact]
    public async Task Refuses_a_plan_built_on_a_zero_reference_max()
    {
        var harness = new TrainingHarness();
        var command = TrainingHarness.SixWeekBlock() with { SquatReferenceMaxKg = 0m };

        await Assert.ThrowsAsync<GriffGym.Domain.Common.DomainException>(
            () => harness.CreateCycle.ExecuteAsync(command, default));
    }

    [Fact]
    public async Task Refuses_a_template_pointing_at_an_unknown_movement()
    {
        var harness = new TrainingHarness();
        var command = TrainingHarness.SixWeekBlock();
        var stripped = command with { Exercises = [] };

        await Assert.ThrowsAsync<NotFoundException>(
            () => harness.CreateCycle.ExecuteAsync(stripped, default));
    }
}

public sealed class GetTrainingCyclesUseCaseTests
{
    [Fact]
    public async Task Counts_progress_from_the_training_log()
    {
        // Not tracked as its own state, so it cannot drift away from what was actually trained.
        var harness = new TrainingHarness();
        var created = await harness.CreateCycle.ExecuteAsync(TrainingHarness.SixWeekBlock(), default);

        var firstUnit = created.Cycle.Program.Weeks[0].Workouts[0];
        var started = await harness.CreateWorkout.ExecuteAsync(
            new CreateWorkoutSessionCommand(
                null, created.Cycle.Id, null, firstUnit.Id, null, null, null, null,
                WorkoutSessionStatus.InProgress, null, null, null, null, null),
            default);

        await harness.CompleteWorkout.ExecuteAsync(
            new FinishWorkoutSessionCommand(started.Session.Id, null, null),
            default);

        var summaries = await harness.GetCycles.ExecuteAsync(default);

        var summary = Assert.Single(summaries);
        Assert.Equal(18, summary.PlannedWorkouts);
        Assert.Equal(1, summary.CompletedWorkouts);
        Assert.Equal(1, summary.CurrentWeekNumber);
        Assert.Equal(0, summary.CompletedWeeks);
        Assert.True(summary.Weeks[0].IsStarted);
        Assert.False(summary.Weeks[0].IsComplete);
    }

    [Fact]
    public async Task Lists_newest_first()
    {
        var harness = new TrainingHarness();
        await harness.CreateCycle.ExecuteAsync(TrainingHarness.SixWeekBlock(Guid.NewGuid(), 1), default);
        await harness.CreateCycle.ExecuteAsync(TrainingHarness.SixWeekBlock(Guid.NewGuid(), 2), default);

        var summaries = await harness.GetCycles.ExecuteAsync(default);

        Assert.Equal([2, 1], summaries.Select(summary => summary.CycleNumber));
    }
}

public sealed class ReferenceMaxUseCaseTests
{
    [Fact]
    public async Task Creates_a_planning_number_on_first_use_and_updates_it_afterwards()
    {
        var harness = new TrainingHarness();

        var created = await harness.UpdateReferenceMax.ExecuteAsync(
            new UpdateReferenceMaxCommand(LiftType.Squat, 210m, null),
            default);

        var updated = await harness.UpdateReferenceMax.ExecuteAsync(
            new UpdateReferenceMaxCommand(LiftType.Squat, 215m, null),
            default);

        // A PUT: one squat max per lifter, however many times it is set.
        Assert.Equal(created.Id, updated.Id);
        Assert.Equal(215m, updated.ValueKg);
        Assert.Single(await harness.GetReferenceMaxes.ExecuteAsync(default));
    }

    [Fact]
    public async Task Keeps_the_identifier_the_phone_generated()
    {
        var harness = new TrainingHarness();
        var localId = Guid.NewGuid();

        var created = await harness.UpdateReferenceMax.ExecuteAsync(
            new UpdateReferenceMaxCommand(LiftType.Deadlift, 225m, localId),
            default);

        Assert.Equal(localId, created.Id);
    }

    [Fact]
    public async Task Changing_it_does_not_touch_a_cycle_already_planned()
    {
        // The rule the two tables exist to keep apart.
        var harness = new TrainingHarness();
        var cycle = await harness.CreateCycle.ExecuteAsync(TrainingHarness.SixWeekBlock(), default);

        await harness.UpdateReferenceMax.ExecuteAsync(
            new UpdateReferenceMaxCommand(LiftType.Squat, 999m, null),
            default);

        var reloaded = await harness.GetCycle.ExecuteAsync(cycle.Cycle.Id, default);
        Assert.Equal(210m, reloaded.ReferenceMaxes.SquatKg);
    }
}
