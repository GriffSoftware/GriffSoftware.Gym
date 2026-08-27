using GriffGym.Application.Common;
using GriffGym.Application.Cycles;
using GriffGym.Application.Workouts;
using GriffGym.Domain.Training;

namespace GriffGym.Application.Tests;

public sealed class WorkoutUseCaseTests
{
    private static async Task<(TrainingHarness Harness, TrainingCycleView Cycle)> WithCycleAsync()
    {
        var harness = new TrainingHarness();
        var created = await harness.CreateCycle.ExecuteAsync(TrainingHarness.SixWeekBlock(), default);

        return (harness, created.Cycle);
    }

    private static CreateWorkoutSessionCommand StartOf(TrainingCycleView cycle, Guid? id = null) =>
        new(
            id,
            cycle.Id,
            null,
            cycle.Program.Weeks[0].Workouts[0].Id,
            null, null, null, null,
            WorkoutSessionStatus.InProgress,
            null, null, null, null, null);

    [Fact]
    public async Task Starting_a_workout_snapshots_the_planned_unit()
    {
        var (harness, cycle) = await WithCycleAsync();
        var template = cycle.Program.Weeks[0].Workouts[0];

        var result = await harness.CreateWorkout.ExecuteAsync(StartOf(cycle), default);

        Assert.True(result.WasCreated);
        Assert.Equal(template.Title, result.Session.Title);
        Assert.Equal(1, result.Session.WeekNumber);
        Assert.Equal(template.Id, result.Session.WorkoutTemplateId);

        var sets = result.Session.Exercises.Single().Sets;
        Assert.Equal(2, sets.Count);
        Assert.Equal(template.Exercises[0].PlannedSets[0].WeightKg, sets[0].PlannedWeightKg);
        Assert.All(sets, set => Assert.Null(set.ActualWeightKg));
    }

    [Fact]
    public async Task Refuses_a_second_workout_while_one_is_running()
    {
        // "Which workout am I in?" has to have one answer.
        var (harness, cycle) = await WithCycleAsync();
        await harness.CreateWorkout.ExecuteAsync(StartOf(cycle), default);

        var exception = await Assert.ThrowsAsync<ConflictException>(
            () => harness.CreateWorkout.ExecuteAsync(StartOf(cycle), default));

        Assert.Contains("still in progress", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public async Task Sending_the_same_creation_twice_logs_one_workout()
    {
        var (harness, cycle) = await WithCycleAsync();
        var sessionId = Guid.NewGuid();

        var first = await harness.CreateWorkout.ExecuteAsync(StartOf(cycle, sessionId), default);
        var second = await harness.CreateWorkout.ExecuteAsync(StartOf(cycle, sessionId), default);

        Assert.True(first.WasCreated);
        Assert.False(second.WasCreated);
        Assert.Single(harness.Sessions.All);
    }

    [Fact]
    public async Task Logging_a_set_writes_the_result_and_leaves_the_plan_alone()
    {
        var (harness, cycle) = await WithCycleAsync();
        var started = await harness.CreateWorkout.ExecuteAsync(StartOf(cycle), default);
        var set = started.Session.Exercises.Single().Sets[0];

        var updated = await harness.LogSet.ExecuteAsync(
            new LogSetCommand(started.Session.Id, set.Id, null, 190m, 3, 8.5m, true, "solid"),
            default);

        var logged = updated.Exercises.Single().Sets[0];
        Assert.Equal(set.PlannedWeightKg, logged.PlannedWeightKg);
        Assert.Equal(190m, logged.ActualWeightKg);
        Assert.Equal(3, logged.ActualReps);
        Assert.Equal(8.5m, logged.ActualRpe);
        Assert.True(logged.Completed);
        Assert.Equal(570m, logged.VolumeKg);
    }

    [Fact]
    public async Task Completing_freezes_the_tonnage()
    {
        var (harness, cycle) = await WithCycleAsync();
        var started = await harness.CreateWorkout.ExecuteAsync(StartOf(cycle), default);
        var sets = started.Session.Exercises.Single().Sets;

        await harness.LogSet.ExecuteAsync(
            new LogSetCommand(started.Session.Id, sets[0].Id, null, 190m, 3, 8m, true, null),
            default);
        await harness.LogSet.ExecuteAsync(
            new LogSetCommand(started.Session.Id, sets[1].Id, null, 175m, 3, 7m, true, null),
            default);

        harness.Clock.Advance(TimeSpan.FromMinutes(72));

        var completed = await harness.CompleteWorkout.ExecuteAsync(
            new FinishWorkoutSessionCommand(started.Session.Id, null, null),
            default);

        Assert.Equal(WorkoutSessionStatus.Completed, completed.Status);
        Assert.Equal(1095m, completed.TotalVolumeKg);
        Assert.Equal(72 * 60, completed.DurationSeconds);
        Assert.Equal(2, completed.CompletedSets);
    }

    [Fact]
    public async Task A_completed_workout_stops_accepting_sets()
    {
        var (harness, cycle) = await WithCycleAsync();
        var started = await harness.CreateWorkout.ExecuteAsync(StartOf(cycle), default);
        var set = started.Session.Exercises.Single().Sets[0];

        await harness.CompleteWorkout.ExecuteAsync(
            new FinishWorkoutSessionCommand(started.Session.Id, null, null),
            default);

        await Assert.ThrowsAsync<GriffGym.Domain.Common.DomainException>(
            () => harness.LogSet.ExecuteAsync(
                new LogSetCommand(started.Session.Id, set.Id, null, 100m, 5, null, true, null),
                default));
    }

    [Fact]
    public async Task Refuses_a_write_against_a_revision_that_has_moved_on()
    {
        // Two phones open on the same workout: the stale one is told, not silently obeyed.
        var (harness, cycle) = await WithCycleAsync();
        var started = await harness.CreateWorkout.ExecuteAsync(StartOf(cycle), default);
        var set = started.Session.Exercises.Single().Sets[0];

        var exception = await Assert.ThrowsAsync<ConcurrencyConflictException>(
            () => harness.LogSet.ExecuteAsync(
                new LogSetCommand(started.Session.Id, set.Id, 99, 190m, 3, 8m, true, null),
                default));

        Assert.Equal(99, exception.ExpectedVersion);
    }

    [Fact]
    public async Task Cannot_reach_another_lifters_workout()
    {
        var (harness, cycle) = await WithCycleAsync();
        var started = await harness.CreateWorkout.ExecuteAsync(StartOf(cycle), default);

        harness.CurrentUser.UserId = Guid.NewGuid();

        // Not 403: answering "forbidden" would confirm the identifier is real.
        await Assert.ThrowsAsync<NotFoundException>(() => harness.CompleteWorkout.ExecuteAsync(
            new FinishWorkoutSessionCommand(started.Session.Id, null, null),
            default));
    }

    [Fact]
    public async Task The_active_workout_is_the_one_still_running()
    {
        var (harness, cycle) = await WithCycleAsync();

        Assert.Null(await harness.GetActiveWorkout.ExecuteAsync(default));

        var started = await harness.CreateWorkout.ExecuteAsync(StartOf(cycle), default);

        var active = await harness.GetActiveWorkout.ExecuteAsync(default);
        Assert.NotNull(active);
        Assert.Equal(started.Session.Id, active.Id);

        await harness.CompleteWorkout.ExecuteAsync(
            new FinishWorkoutSessionCommand(started.Session.Id, null, null),
            default);

        Assert.Null(await harness.GetActiveWorkout.ExecuteAsync(default));
    }

    [Fact]
    public async Task Uploads_a_workout_that_was_finished_offline()
    {
        // The LOCAL to ACCOUNT path: months of history posted after the fact.
        var harness = new TrainingHarness();

        var result = await harness.CreateWorkout.ExecuteAsync(
            new CreateWorkoutSessionCommand(
                null, null, null, null,
                WeekNumber: 4,
                DayNumber: 2,
                Title: "Deadlift Focus / Bench Light",
                IsDeload: false,
                Status: WorkoutSessionStatus.Completed,
                PerformedOn: new DateOnly(2025, 11, 4),
                StartedAtUtc: TrainingHarness.Start.AddDays(-120),
                FinishedAtUtc: TrainingHarness.Start.AddDays(-120).AddMinutes(80),
                Notes: "back on form",
                Exercises:
                [
                    new ExerciseLogInput(
                        null, 1, null, "Martwy ciąg", ExerciseCategory.Deadlift,
                        ExerciseType.Top, null,
                        [
                            new SetLogInput(
                                null, 1, 200m, 3, 8m, 8m, 200m, 3, 8m, Completed: true, null),
                        ]),
                ]),
            default);

        Assert.Equal(WorkoutSessionStatus.Completed, result.Session.Status);
        Assert.Equal(600m, result.Session.TotalVolumeKg);
        Assert.Equal(new DateOnly(2025, 11, 4), result.Session.PerformedOn);
        Assert.Equal(4800, result.Session.DurationSeconds);
    }

    [Fact]
    public async Task History_is_paginated_newest_first()
    {
        var harness = new TrainingHarness();

        for (var index = 0; index < 5; index++)
        {
            await harness.CreateWorkout.ExecuteAsync(
                new CreateWorkoutSessionCommand(
                    null, null, null, null, 1, 1, $"Session {index}", false,
                    WorkoutSessionStatus.Completed,
                    new DateOnly(2026, 1, index + 1),
                    TrainingHarness.Start.AddDays(index),
                    TrainingHarness.Start.AddDays(index).AddHours(1),
                    null,
                    [
                        new ExerciseLogInput(
                            null, 1, null, "Przysiad", ExerciseCategory.Squat,
                            ExerciseType.Top, null,
                            [new SetLogInput(null, 1, 100m, 5, null, null, 100m, 5, null, true, null)]),
                    ]),
                default);
        }

        var page = await harness.GetHistory.ExecuteAsync(new WorkoutHistoryQuery(1, 2), default);

        Assert.Equal(5, page.TotalCount);
        Assert.Equal(3, page.TotalPages);
        Assert.True(page.HasNextPage);
        Assert.Equal(["Session 4", "Session 3"], page.Items.Select(item => item.Title));
    }

    [Fact]
    public async Task Replacing_the_tree_keeps_the_set_identifiers_the_phone_owns()
    {
        var (harness, cycle) = await WithCycleAsync();
        var started = await harness.CreateWorkout.ExecuteAsync(StartOf(cycle), default);
        var exercise = started.Session.Exercises.Single();

        var updated = await harness.UpdateWorkout.ExecuteAsync(
            new UpdateWorkoutSessionCommand(
                started.Session.Id,
                null,
                "uploaded offline",
                [
                    new ExerciseLogInput(
                        exercise.Id, 1, exercise.ExerciseId, exercise.ExerciseName,
                        exercise.ExerciseCategory, exercise.Type, null,
                        [
                            new SetLogInput(
                                exercise.Sets[0].Id, 1,
                                exercise.Sets[0].PlannedWeightKg, exercise.Sets[0].PlannedReps,
                                8m, 8m,
                                190m, 3, 8.5m, Completed: true, "logged in the basement"),
                        ]),
                ]),
            default);

        Assert.Equal("uploaded offline", updated.Notes);
        Assert.Equal(exercise.Sets[0].Id, updated.Exercises.Single().Sets[0].Id);
        Assert.Equal(190m, updated.Exercises.Single().Sets[0].ActualWeightKg);
    }
}
