using GriffGym.Application.Common;
using GriffGym.Application.ReferenceMaxes;
using GriffGym.Application.State;
using GriffGym.Application.Workouts;
using GriffGym.Domain.Training;

namespace GriffGym.Application.Tests;

public sealed class GetUserApplicationStateUseCaseTests
{
    [Fact]
    public async Task Carries_everything_needed_to_rebuild_an_installation()
    {
        var harness = new TrainingHarness();

        await harness.UpdateReferenceMax.ExecuteAsync(
            new UpdateReferenceMaxCommand(LiftType.Squat, 210m, null), default);
        await harness.UpdateReferenceMax.ExecuteAsync(
            new UpdateReferenceMaxCommand(LiftType.BenchPress, 170m, null), default);
        await harness.UpdateReferenceMax.ExecuteAsync(
            new UpdateReferenceMaxCommand(LiftType.Deadlift, 225m, null), default);

        var cycle = (await harness.CreateCycle.ExecuteAsync(
            TrainingHarness.SixWeekBlock(), default)).Cycle;

        var firstUnit = cycle.Program.Weeks[0].Workouts[0];
        var finished = await harness.CreateWorkout.ExecuteAsync(
            new CreateWorkoutSessionCommand(
                null, cycle.Id, null, firstUnit.Id, null, null, null, null,
                WorkoutSessionStatus.InProgress, null, null, null, null, null),
            default);

        await harness.LogSet.ExecuteAsync(
            new LogSetCommand(
                finished.Session.Id,
                finished.Session.Exercises.Single().Sets[0].Id,
                null, 190m, 3, 8m, true, null),
            default);

        await harness.CompleteWorkout.ExecuteAsync(
            new FinishWorkoutSessionCommand(finished.Session.Id, null, null), default);

        var secondUnit = cycle.Program.Weeks[0].Workouts[1];
        var open = await harness.CreateWorkout.ExecuteAsync(
            new CreateWorkoutSessionCommand(
                null, cycle.Id, null, secondUnit.Id, null, null, null, null,
                WorkoutSessionStatus.InProgress, null, null, null, null, null),
            default);

        var state = await harness.GetState.ExecuteAsync(default);

        Assert.Equal(GetUserApplicationStateUseCase.CurrentSchemaVersion, state.SchemaVersion);
        Assert.Equal("lifter@example.com", state.Profile.Email);

        Assert.Equal(3, state.ReferenceMaxes.Count);
        Assert.Equal(2, state.Exercises.Count);

        // The whole plan, not just its number: history has to survive the template changing.
        var restoredCycle = Assert.Single(state.Cycles);
        Assert.Equal(cycle.Id, state.CurrentCycleId);
        Assert.Equal(6, restoredCycle.Program.Weeks.Count);
        Assert.Equal(18, restoredCycle.Program.Weeks.Sum(week => week.Workouts.Count));
        Assert.NotNull(restoredCycle.Program.CurrentWorkoutTemplateId);

        Assert.Equal(2, state.Workouts.Count);
        Assert.Equal(open.Session.Id, state.ActiveWorkoutId);

        var completed = state.Workouts.Single(
            workout => workout.Status == WorkoutSessionStatus.Completed);
        Assert.Equal(570m, completed.TotalVolumeKg);

        // Planned and actual both present, which is what makes a restored history honest.
        var loggedSet = completed.Exercises.Single().Sets[0];
        Assert.NotNull(loggedSet.PlannedWeightKg);
        Assert.Equal(190m, loggedSet.ActualWeightKg);
        Assert.Equal(8m, loggedSet.ActualRpe);
    }

    [Fact]
    public async Task Is_empty_but_valid_for_a_brand_new_account()
    {
        var harness = new TrainingHarness();

        var state = await harness.GetState.ExecuteAsync(default);

        Assert.Empty(state.ReferenceMaxes);
        Assert.Empty(state.Cycles);
        Assert.Empty(state.Workouts);
        Assert.Null(state.CurrentCycleId);
        Assert.Null(state.ActiveWorkoutId);
        Assert.Equal(0, state.SyncVersion);
    }

    [Fact]
    public async Task Shows_nothing_belonging_to_another_lifter()
    {
        var harness = new TrainingHarness();
        await harness.CreateCycle.ExecuteAsync(TrainingHarness.SixWeekBlock(), default);

        harness.CurrentUser.UserId = Guid.NewGuid();

        // No user row for that id, so the read fails outright rather than returning a document.
        await Assert.ThrowsAsync<NotFoundException>(() => harness.GetState.ExecuteAsync(default));
    }

    [Fact]
    public async Task Needs_somebody_to_be_signed_in()
    {
        var harness = new TrainingHarness();
        harness.CurrentUser.UserId = null;

        await Assert.ThrowsAsync<UnauthenticatedException>(
            () => harness.GetState.ExecuteAsync(default));
    }
}
