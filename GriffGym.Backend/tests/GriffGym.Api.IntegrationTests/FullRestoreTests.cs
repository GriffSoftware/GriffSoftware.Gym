using System.Net;
using System.Net.Http.Json;
using GriffGym.Api.Contracts.V1;
using GriffGym.Domain.Training;
using GriffGym.TestSupport;

namespace GriffGym.Api.IntegrationTests;

/// <summary>
/// The test the whole backend exists for.
///
/// It walks a lifter through a real life of the app — register, enter their maxes, build a
/// cycle, train a workout, finish it, start another and walk away mid-session — and then asks
/// the one question that matters: if their phone went in a river right now, does
/// <c>GET /api/v1/state</c> contain enough to put everything back?
///
/// Not "does it return 200". Every assertion below is a thing the Android app would need to
/// rebuild its Room database and carry on from the exact set the lifter was on.
/// </summary>
public sealed class FullRestoreTests(PostgresFixture fixture) : ApiTest(fixture)
{
    [Fact]
    public async Task A_lifters_whole_installation_can_be_rebuilt_from_the_state_document()
    {
        var lifter = await RegisterLifterAsync("restore@example.com");
        var client = lifter.Client;

        // ---- Reference maxes: the three planning numbers onboarding collects. -------------
        foreach (var (lift, value) in new[]
                 {
                     (LiftType.Squat, SixWeekBlockRequest.SquatMax),
                     (LiftType.BenchPress, SixWeekBlockRequest.BenchMax),
                     (LiftType.Deadlift, SixWeekBlockRequest.DeadliftMax),
                 })
        {
            var response = await client.PutAsJsonAsync(
                $"/api/v1/reference-maxes/{lift}",
                new UpdateReferenceMaxRequest(value, null),
                GriffGymApiFactory.Json);

            Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        }

        // ---- Cycle 1, with the whole six-week plan generated on the phone. ----------------
        var cycle = await (await client.PostAsJsonAsync(
                "/api/v1/cycles", SixWeekBlockRequest.Build(), GriffGymApiFactory.Json))
            .ReadSuccessAsync<CycleResponse>();

        var weekOne = cycle.Program.Weeks[0];

        // ---- Train week 1 day I and finish it. -------------------------------------------
        var firstWorkout = await (await client.PostAsJsonAsync(
                "/api/v1/workouts",
                new CreateWorkoutRequest(
                    null, cycle.Id, weekOne.Id, weekOne.Workouts[0].Id,
                    null, null, null, null, null, null, null, null, null, null),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        var loggedResults = new List<(Guid SetId, decimal Weight, int Reps, decimal Rpe)>();
        var version = firstWorkout.Version;

        foreach (var set in firstWorkout.Exercises.SelectMany(exercise => exercise.Sets))
        {
            var actual = set.PlannedWeightKg ?? 100m;
            var reps = set.PlannedReps ?? 5;
            const decimal rpe = 8m;

            var updated = await (await client.PutAsJsonAsync(
                    $"/api/v1/workouts/{firstWorkout.Id}/sets/{set.Id}",
                    new LogSetRequest(version, actual, reps, rpe, true, null),
                    GriffGymApiFactory.Json))
                .ReadSuccessAsync<WorkoutResponse>();

            version = updated.Version;
            loggedResults.Add((set.Id, actual, reps, rpe));
        }

        var completed = await (await client.PostAsJsonAsync(
                $"/api/v1/workouts/{firstWorkout.Id}/complete",
                new FinishWorkoutRequest(version, null),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        Assert.Equal(WorkoutSessionStatus.Completed, completed.Status);

        // ---- Advance the plan, exactly as finishing a unit does. -------------------------
        await client.PutAsJsonAsync(
            $"/api/v1/cycles/{cycle.Id}/progress",
            new UpdateCycleProgressRequest(weekOne.Workouts[1].Id),
            GriffGymApiFactory.Json);

        // ---- Start week 1 day II, log one set, and walk away. ----------------------------
        var openWorkout = await (await client.PostAsJsonAsync(
                "/api/v1/workouts",
                new CreateWorkoutRequest(
                    null, cycle.Id, weekOne.Id, weekOne.Workouts[1].Id,
                    null, null, null, null, null, null, null, null, null, null),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        var openSetId = openWorkout.Exercises[0].Sets[0].Id;

        await client.PutAsJsonAsync(
            $"/api/v1/workouts/{openWorkout.Id}/sets/{openSetId}",
            new LogSetRequest(openWorkout.Version, 187.5m, 3, 8.5m, true, "first set in"),
            GriffGymApiFactory.Json);

        // =================================================================================
        // The phone is gone. A fresh install signs in and asks once.
        // =================================================================================
        var state = await (await client.GetAsync("/api/v1/state"))
            .ReadSuccessAsync<ApplicationStateResponse>();

        Assert.Equal(1, state.SchemaVersion);
        Assert.True(state.SyncVersion > 0, "the restore cursor must be set");

        // Who they are.
        Assert.Equal(lifter.Id, state.Profile.Id);
        Assert.Equal("restore@example.com", state.Profile.Email);

        // The planning numbers.
        Assert.Equal(3, state.ReferenceMaxes.Count);
        Assert.Equal(
            SixWeekBlockRequest.SquatMax,
            state.ReferenceMaxes.Single(max => max.Lift == LiftType.Squat).ValueKg);

        // The movement catalogue the plan refers to.
        Assert.Equal(2, state.Exercises.Count);
        Assert.Contains(state.Exercises, exercise => exercise.Name == "Przysiad");

        // The cycle, with the maxes it was built from frozen onto it.
        var restoredCycle = Assert.Single(state.Cycles);
        Assert.Equal(cycle.Id, state.CurrentCycleId);
        Assert.Equal(1, restoredCycle.CycleNumber);
        Assert.Equal(SixWeekBlockRequest.SquatMax, restoredCycle.ReferenceMaxes.SquatKg);
        Assert.Equal(SixWeekBlockRequest.BenchMax, restoredCycle.ReferenceMaxes.BenchPressKg);
        Assert.Equal(SixWeekBlockRequest.DeadliftMax, restoredCycle.ReferenceMaxes.DeadliftKg);

        // The whole plan, not a rule for regenerating it. If the app's template ever changes,
        // cycle 1 still restores as the block that was actually trained.
        Assert.Equal(6, restoredCycle.Program.Weeks.Count);
        Assert.Equal(18, restoredCycle.Program.Weeks.Sum(week => week.Workouts.Count));
        Assert.Equal(
            54,
            restoredCycle.Program.Weeks
                .SelectMany(week => week.Workouts)
                .SelectMany(workout => workout.Exercises)
                .Sum(exercise => exercise.PlannedSets.Count));

        // Weeks 1-5 train, week 6 deloads at half the reference max.
        Assert.Equal(
            5,
            restoredCycle.Program.Weeks.Count(week => week.Type == TrainingWeekType.Training));
        var deload = restoredCycle.Program.Weeks.Single(week => week.IsDeload);
        Assert.Equal(6, deload.WeekNumber);
        Assert.Equal(
            SixWeekBlockRequest.SquatMax / 2m,
            deload.Workouts[0].Exercises[0].PlannedSets[0].WeightKg);

        // The sequence, so "what do I train next?" survives the restore.
        var sequence = restoredCycle.Program.Weeks
            .SelectMany(week => week.Workouts)
            .Select(workout => workout.SequenceNumber)
            .Order()
            .ToList();
        Assert.Equal(Enumerable.Range(1, 18), sequence);

        // Where the lifter is inside the plan.
        Assert.Equal(weekOne.Workouts[1].Id, restoredCycle.Program.CurrentWorkoutTemplateId);

        // ---- The training log: one finished workout and one still open. ------------------
        Assert.Equal(2, state.Workouts.Count);

        var restoredCompleted = state.Workouts.Single(
            workout => workout.Status == WorkoutSessionStatus.Completed);

        Assert.Equal(firstWorkout.Id, restoredCompleted.Id);
        Assert.Equal(cycle.Id, restoredCompleted.TrainingCycleId);
        Assert.Equal(weekOne.Workouts[0].Id, restoredCompleted.WorkoutTemplateId);
        Assert.Equal(1, restoredCompleted.WeekNumber);
        Assert.Equal(completed.TotalVolumeKg, restoredCompleted.TotalVolumeKg);
        Assert.NotNull(restoredCompleted.FinishedAtUtc);

        // Every set, with what was asked for next to what happened.
        var restoredSets = restoredCompleted.Exercises
            .SelectMany(exercise => exercise.Sets)
            .ToDictionary(set => set.Id);

        Assert.Equal(loggedResults.Count, restoredSets.Count);

        foreach (var (setId, weight, reps, rpe) in loggedResults)
        {
            var set = restoredSets[setId];

            Assert.NotNull(set.PlannedWeightKg);
            Assert.NotNull(set.PlannedReps);
            Assert.Equal(weight, set.ActualWeightKg);
            Assert.Equal(reps, set.ActualReps);
            Assert.Equal(rpe, set.ActualRpe);
            Assert.True(set.Completed);
        }

        // ---- The workout still open: the lifter carries on from the set they were on. -----
        Assert.Equal(openWorkout.Id, state.ActiveWorkoutId);

        var restoredOpen = state.Workouts.Single(workout => workout.Id == openWorkout.Id);

        Assert.Equal(WorkoutSessionStatus.InProgress, restoredOpen.Status);
        Assert.Null(restoredOpen.FinishedAtUtc);
        Assert.Equal(2, restoredOpen.DayNumber);

        var restoredOpenSet = restoredOpen.Exercises
            .SelectMany(exercise => exercise.Sets)
            .Single(set => set.Id == openSetId);

        Assert.Equal(187.5m, restoredOpenSet.ActualWeightKg);
        Assert.Equal(8.5m, restoredOpenSet.ActualRpe);
        Assert.Equal("first set in", restoredOpenSet.Notes);
        Assert.True(restoredOpenSet.Completed);

        // The rest of that workout is still ahead of them, planned and untouched.
        var untouched = restoredOpen.Exercises
            .SelectMany(exercise => exercise.Sets)
            .Where(set => set.Id != openSetId)
            .ToList();

        Assert.NotEmpty(untouched);
        Assert.All(untouched, set => Assert.False(set.Completed));
        Assert.All(untouched, set => Assert.Null(set.ActualWeightKg));
        Assert.All(untouched, set => Assert.NotNull(set.PlannedWeightKg));

        // ---- Cycle progress, derived from the log rather than stored beside it. -----------
        var summary = Assert.Single(
            await (await client.GetAsync("/api/v1/cycles")).ReadSuccessAsync<List<CycleSummaryResponse>>());

        Assert.Equal(18, summary.PlannedWorkouts);
        Assert.Equal(1, summary.CompletedWorkouts);
        Assert.Equal(1, summary.CurrentWeekNumber);

        // ---- And asking again changes nothing. -------------------------------------------
        var second = await (await client.GetAsync("/api/v1/state"))
            .ReadSuccessAsync<ApplicationStateResponse>();

        Assert.Equal(state.SyncVersion, second.SyncVersion);
        Assert.Equal(state.Workouts.Count, second.Workouts.Count);
        Assert.Equal(state.ActiveWorkoutId, second.ActiveWorkoutId);
    }

    [Fact]
    public async Task Six_months_of_local_history_can_be_uploaded_after_the_fact()
    {
        // The LOCAL to ACCOUNT path: the phone owns identifiers the server has never seen, and
        // the sessions it uploads are already finished. Nothing here may assume the server
        // created the data first.
        var lifter = await RegisterLifterAsync("migrating@example.com");
        var client = lifter.Client;

        var localSessionId = Guid.NewGuid();
        var localExerciseLogId = Guid.NewGuid();
        var localSetId = Guid.NewGuid();

        var request = new CreateWorkoutRequest(
            localSessionId,
            null, null, null,
            WeekNumber: 3,
            DayNumber: 2,
            Title: "Deadlift Focus / Bench Light",
            IsDeload: false,
            Status: WorkoutSessionStatus.Completed,
            PerformedOn: new DateOnly(2025, 11, 4),
            StartedAtUtc: new DateTimeOffset(2025, 11, 4, 17, 0, 0, TimeSpan.Zero),
            FinishedAtUtc: new DateTimeOffset(2025, 11, 4, 18, 20, 0, TimeSpan.Zero),
            Notes: "uploaded from the phone",
            Exercises:
            [
                new ExerciseLogRequest(
                    localExerciseLogId, 1, null, "Martwy ciąg", ExerciseCategory.Deadlift,
                    ExerciseType.Top, null,
                    [
                        new SetLogRequest(
                            localSetId, 1, 200m, 3, 8m, 8m, 202.5m, 3, 8.5m, true, "PB"),
                    ]),
            ]);

        var created = await (await client.PostAsJsonAsync(
                "/api/v1/workouts", request, GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        // The identifiers the phone invented are the identifiers the server keeps.
        Assert.Equal(localSessionId, created.Id);
        Assert.Equal(localExerciseLogId, created.Exercises[0].Id);
        Assert.Equal(localSetId, created.Exercises[0].Sets[0].Id);
        Assert.Equal(WorkoutSessionStatus.Completed, created.Status);
        Assert.Equal(607.5m, created.TotalVolumeKg);
        Assert.Equal(80 * 60, created.DurationSeconds);

        // Re-sending it after a timeout is not a second workout.
        var replay = await client.PostAsJsonAsync(
            "/api/v1/workouts", request, GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.OK, replay.StatusCode);

        var state = await (await client.GetAsync("/api/v1/state"))
            .ReadSuccessAsync<ApplicationStateResponse>();

        var restored = Assert.Single(state.Workouts);
        Assert.Equal(localSessionId, restored.Id);
        Assert.Equal(new DateOnly(2025, 11, 4), restored.PerformedOn);
        Assert.Equal(202.5m, restored.Exercises[0].Sets[0].ActualWeightKg);
        Assert.Equal(200m, restored.Exercises[0].Sets[0].PlannedWeightKg);
        Assert.Null(state.ActiveWorkoutId);
    }
}
