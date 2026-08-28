using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using GriffGym.Api.Contracts.V1;
using GriffGym.Domain.Training;
using GriffGym.Infrastructure.Persistence.Entities;
using GriffGym.TestSupport;
using Microsoft.EntityFrameworkCore;

namespace GriffGym.Api.IntegrationTests;

/// <summary>
/// The tests that make "delete my account" mean what it says.
///
/// A deletion feature that leaves anything behind is worse than none at all, because the lifter
/// stops asking. So these do not check for a 204 and move on — they build a full installation's
/// worth of data, delete it, and then go behind the API and count rows in every table that can
/// hold something of that lifter's. Anything a future migration adds to the ownership graph and
/// forgets to add to the deletion path shows up here as a non-zero count.
///
/// The other half is the one that would be far worse to get wrong: user A's deletion must not
/// touch user B. That is asserted by building two complete accounts and comparing B's data
/// before and after.
/// </summary>
public sealed class AccountDeletionTests(PostgresFixture fixture) : ApiTest(fixture)
{
    [Fact]
    public async Task Deleting_an_account_removes_every_record_the_lifter_owned()
    {
        var lifter = await RegisterLifterAsync("erase-me@example.com");
        await BuildAWholeTrainingLifeAsync(lifter);

        // A couple of extra sessions, so the test is not merely deleting the one refresh token
        // registration handed out.
        await SignInAgainAsync(lifter.Email);
        await SignInAgainAsync(lifter.Email);

        await AssertNothingIsEmptyBeforeDeletionAsync(lifter.Id);

        var response = await lifter.Client.DeleteAsync("/api/v1/users/me");

        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
        Assert.Equal(0, response.Content.Headers.ContentLength ?? 0);

        await AssertNothingSurvivesAsync(lifter.Id);
    }

    [Fact]
    public async Task Deleting_one_account_leaves_every_other_account_untouched()
    {
        // The critical one. Everything else on this page is about thoroughness; this is about
        // not destroying a stranger's training history.
        var leaving = await RegisterLifterAsync("leaving@example.com");
        var staying = await RegisterLifterAsync("staying@example.com");

        await BuildAWholeTrainingLifeAsync(leaving);
        await BuildAWholeTrainingLifeAsync(staying);

        var before = await CountEverythingAsync(staying.Id);

        var response = await leaving.Client.DeleteAsync("/api/v1/users/me");
        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);

        await AssertNothingSurvivesAsync(leaving.Id);

        Assert.Equal(before, await CountEverythingAsync(staying.Id));

        // And the account still works, not merely still exists.
        var state = await (await staying.Client.GetAsync("/api/v1/state"))
            .ReadSuccessAsync<ApplicationStateResponse>();

        Assert.Equal(staying.Id, state.Profile.Id);
        Assert.NotEmpty(state.Cycles);
        Assert.NotEmpty(state.Workouts);
        Assert.NotEmpty(state.ReferenceMaxes);
    }

    [Fact]
    public async Task The_old_credentials_cannot_bring_a_deleted_account_back()
    {
        var lifter = await RegisterLifterAsync("gone@example.com");

        await lifter.Client.DeleteAsync("/api/v1/users/me");

        var response = await CreateClient().PostAsJsonAsync(
            "/api/v1/auth/login",
            new LoginRequest("gone@example.com", "correct horse battery", "pixel-9"),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Registering_the_same_address_again_produces_a_new_account_with_no_history()
    {
        var lifter = await RegisterLifterAsync("reused@example.com");
        await BuildAWholeTrainingLifeAsync(lifter);

        await lifter.Client.DeleteAsync("/api/v1/users/me");

        // The address is free again — nothing about deletion reserves it — but what comes back
        // is a new account. None of the old training data is recoverable, by design.
        var reborn = await RegisterLifterAsync("reused@example.com");

        Assert.NotEqual(lifter.Id, reborn.Id);

        var state = await (await reborn.Client.GetAsync("/api/v1/state"))
            .ReadSuccessAsync<ApplicationStateResponse>();

        Assert.Empty(state.Cycles);
        Assert.Empty(state.Workouts);
        Assert.Empty(state.ReferenceMaxes);
        Assert.Empty(state.Exercises);
    }

    [Fact]
    public async Task A_refresh_token_from_a_deleted_account_is_refused()
    {
        var lifter = await RegisterLifterAsync("refresh-after@example.com");
        var refreshToken = lifter.Credentials.RefreshToken;

        await lifter.Client.DeleteAsync("/api/v1/users/me");

        var response = await CreateClient().PostAsJsonAsync(
            "/api/v1/auth/refresh",
            new RefreshRequest(refreshToken, "pixel-9"),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Every_session_on_every_device_is_refused_after_deletion()
    {
        var lifter = await RegisterLifterAsync("many-devices@example.com");

        // A second phone and a tablet, each holding their own live session.
        var secondDevice = await SignInAgainAsync(lifter.Email);
        var thirdDevice = await SignInAgainAsync(lifter.Email);

        await lifter.Client.DeleteAsync("/api/v1/users/me");

        foreach (var session in new[] { secondDevice, thirdDevice })
        {
            var refreshed = await CreateClient().PostAsJsonAsync(
                "/api/v1/auth/refresh",
                new RefreshRequest(session.RefreshToken, "other-device"),
                GriffGymApiFactory.Json);

            Assert.Equal(HttpStatusCode.Unauthorized, refreshed.StatusCode);

            Assert.Equal(
                HttpStatusCode.Unauthorized,
                (await GetStateWithAsync(session.AccessToken)).StatusCode);
        }
    }

    /// <summary>
    /// The one an access token's statelessness would otherwise get wrong.
    ///
    /// A JWT stays cryptographically valid for its full fifteen minutes no matter what happens
    /// to the account behind it. Every protected endpoint is checked, not just the one that
    /// happens to load the user row, because "deleted" has to mean deleted everywhere.
    /// </summary>
    [Theory]
    [InlineData("/api/v1/users/me")]
    [InlineData("/api/v1/state")]
    [InlineData("/api/v1/cycles")]
    [InlineData("/api/v1/workouts")]
    [InlineData("/api/v1/workouts/active")]
    [InlineData("/api/v1/reference-maxes")]
    [InlineData("/api/v1/exercises")]
    public async Task The_access_token_stops_working_the_moment_the_account_does(string path)
    {
        var lifter = await RegisterLifterAsync();
        await BuildAWholeTrainingLifeAsync(lifter);

        await lifter.Client.DeleteAsync("/api/v1/users/me");

        // Same client, same still-unexpired bearer token as a second ago.
        var response = await lifter.Client.GetAsync(path);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Repeating_the_delete_request_is_refused_rather_than_run_twice()
    {
        var lifter = await RegisterLifterAsync("twice@example.com");
        await BuildAWholeTrainingLifeAsync(lifter);

        Assert.Equal(
            HttpStatusCode.NoContent,
            (await lifter.Client.DeleteAsync("/api/v1/users/me")).StatusCode);

        // A phone that retried after a timeout. The account is already gone, so the token no
        // longer stands for anything — and nothing is deleted a second time.
        Assert.Equal(
            HttpStatusCode.Unauthorized,
            (await lifter.Client.DeleteAsync("/api/v1/users/me")).StatusCode);

        await AssertNothingSurvivesAsync(lifter.Id);
    }

    [Fact]
    public async Task An_unauthenticated_caller_cannot_delete_anything()
    {
        var lifter = await RegisterLifterAsync("safe@example.com");
        await BuildAWholeTrainingLifeAsync(lifter);

        var response = await CreateClient().DeleteAsync("/api/v1/users/me");

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        await AssertNothingIsEmptyBeforeDeletionAsync(lifter.Id);
    }

    // ----------------------------------------------------------------------------------------
    // Building a lifter worth deleting, and counting what is left of them.
    // ----------------------------------------------------------------------------------------

    /// <summary>
    /// Reference maxes, a six-week cycle with its whole plan, one finished workout with every
    /// set logged, and a second one left open mid-session. Every table in the ownership graph
    /// ends up with rows in it.
    /// </summary>
    private static async Task BuildAWholeTrainingLifeAsync(TestLifter lifter)
    {
        var client = lifter.Client;

        foreach (var (lift, value) in new[]
                 {
                     (LiftType.Squat, SixWeekBlockRequest.SquatMax),
                     (LiftType.BenchPress, SixWeekBlockRequest.BenchMax),
                     (LiftType.Deadlift, SixWeekBlockRequest.DeadliftMax),
                 })
        {
            var set = await client.PutAsJsonAsync(
                $"/api/v1/reference-maxes/{lift}",
                new UpdateReferenceMaxRequest(value, null),
                GriffGymApiFactory.Json);

            set.EnsureSuccessStatusCode();
        }

        var cycle = await (await client.PostAsJsonAsync(
                "/api/v1/cycles", SixWeekBlockRequest.Build(), GriffGymApiFactory.Json))
            .ReadSuccessAsync<CycleResponse>();

        var weekOne = cycle.Program.Weeks[0];

        var finished = await (await client.PostAsJsonAsync(
                "/api/v1/workouts",
                new CreateWorkoutRequest(
                    null, cycle.Id, weekOne.Id, weekOne.Workouts[0].Id,
                    null, null, null, null, null, null, null, null, null, null),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        var version = finished.Version;

        foreach (var set in finished.Exercises.SelectMany(exercise => exercise.Sets))
        {
            var logged = await (await client.PutAsJsonAsync(
                    $"/api/v1/workouts/{finished.Id}/sets/{set.Id}",
                    new LogSetRequest(
                        version, set.PlannedWeightKg ?? 100m, set.PlannedReps ?? 5, 8m, true, null),
                    GriffGymApiFactory.Json))
                .ReadSuccessAsync<WorkoutResponse>();

            version = logged.Version;
        }

        await (await client.PostAsJsonAsync(
                $"/api/v1/workouts/{finished.Id}/complete",
                new FinishWorkoutRequest(version, null),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        // And one left running, because an interrupted session is a state a real account is
        // very often in when somebody decides to leave.
        await (await client.PostAsJsonAsync(
                "/api/v1/workouts",
                new CreateWorkoutRequest(
                    null, cycle.Id, weekOne.Id, weekOne.Workouts[1].Id,
                    null, null, null, null, null, null, null, null, null, null),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();
    }

    private async Task<AuthenticationResponse> SignInAgainAsync(string email) =>
        await (await CreateClient().PostAsJsonAsync(
                "/api/v1/auth/login",
                new LoginRequest(email, "correct horse battery", $"device-{Guid.NewGuid():N}"),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<AuthenticationResponse>();

    private async Task<HttpResponseMessage> GetStateWithAsync(string accessToken)
    {
        var client = CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", accessToken);

        return await client.GetAsync("/api/v1/state");
    }

    /// <summary>
    /// Counted straight out of PostgreSQL, not through the API, and without the
    /// <c>deleted_at_utc IS NULL</c> filter every read path applies — a soft delete would pass
    /// an API-level check while leaving the whole dataset sitting in the active database.
    /// </summary>
    private async Task<OwnedRowCounts> CountEverythingAsync(Guid userId)
    {
        await using var context = Fixture.CreateContext();

        return new OwnedRowCounts(
            Users: await context.Set<UserRecord>().CountAsync(row => row.Id == userId),
            RefreshTokens: await context.Set<RefreshTokenRecord>()
                .CountAsync(row => row.UserId == userId),
            ReferenceMaxes: await context.Set<ReferenceMaxRecord>()
                .CountAsync(row => row.UserId == userId),
            Exercises: await context.Set<ExerciseRecord>().CountAsync(row => row.UserId == userId),
            Cycles: await context.Set<TrainingCycleRecord>()
                .CountAsync(row => row.UserId == userId),
            Programs: await context.Set<TrainingProgramRecord>()
                .CountAsync(row => row.Cycle!.UserId == userId),
            Weeks: await context.Set<TrainingWeekRecord>()
                .CountAsync(row => row.Program!.Cycle!.UserId == userId),
            WorkoutTemplates: await context.Set<WorkoutTemplateRecord>()
                .CountAsync(row => row.Week!.Program!.Cycle!.UserId == userId),
            ExerciseTemplates: await context.Set<ExerciseTemplateRecord>()
                .CountAsync(row => row.Workout!.Week!.Program!.Cycle!.UserId == userId),
            PlannedSets: await context.Set<PlannedSetRecord>()
                .CountAsync(row => row.ExerciseTemplate!.Workout!.Week!.Program!.Cycle!.UserId == userId),
            WorkoutSessions: await context.Set<WorkoutSessionRecord>()
                .CountAsync(row => row.UserId == userId),
            ExerciseLogs: await context.Set<ExerciseLogRecord>()
                .CountAsync(row => row.Session!.UserId == userId),
            SetLogs: await context.Set<SetLogRecord>()
                .CountAsync(row => row.ExerciseLog!.Session!.UserId == userId));
    }

    /// <summary>
    /// Guards against the deletion tests passing because the fixture was empty all along.
    /// </summary>
    private async Task AssertNothingIsEmptyBeforeDeletionAsync(Guid userId)
    {
        var counts = await CountEverythingAsync(userId);

        Assert.Equal(1, counts.Users);

        foreach (var (table, count) in counts.EverythingOwned())
        {
            Assert.True(count > 0, $"Expected the fixture to have created {table} rows, found none.");
        }
    }

    private async Task AssertNothingSurvivesAsync(Guid userId)
    {
        var counts = await CountEverythingAsync(userId);

        Assert.Equal(0, counts.Users);

        foreach (var (table, count) in counts.EverythingOwned())
        {
            Assert.True(count == 0, $"{table} still holds {count} row(s) for a deleted account.");
        }
    }

    private sealed record OwnedRowCounts(
        int Users,
        int RefreshTokens,
        int ReferenceMaxes,
        int Exercises,
        int Cycles,
        int Programs,
        int Weeks,
        int WorkoutTemplates,
        int ExerciseTemplates,
        int PlannedSets,
        int WorkoutSessions,
        int ExerciseLogs,
        int SetLogs)
    {
        /// <summary>Every table but <c>user</c>, named, so a failure says which one.</summary>
        public IEnumerable<(string Table, int Count)> EverythingOwned()
        {
            yield return ("refresh_token", RefreshTokens);
            yield return ("reference_max", ReferenceMaxes);
            yield return ("exercise", Exercises);
            yield return ("training_cycle", Cycles);
            yield return ("training_program", Programs);
            yield return ("training_week", Weeks);
            yield return ("workout_template", WorkoutTemplates);
            yield return ("exercise_template", ExerciseTemplates);
            yield return ("planned_set", PlannedSets);
            yield return ("workout_session", WorkoutSessions);
            yield return ("exercise_log", ExerciseLogs);
            yield return ("set_log", SetLogs);
        }
    }
}
