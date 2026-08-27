using System.Net;
using System.Net.Http.Json;
using GriffGym.Api.Contracts.V1;
using GriffGym.Domain.Training;
using GriffGym.TestSupport;

namespace GriffGym.Api.IntegrationTests;

/// <summary>
/// The tests this API exists to keep passing.
///
/// Every one of them is the same shape: user A creates something, user B — a perfectly valid,
/// authenticated account — asks for it by its exact identifier, and is told it does not exist.
/// Not 403, which would confirm the identifier is real and turn a list of GUIDs into a
/// membership oracle. If any of these ever go green-to-red, the answer is not to relax the test.
/// </summary>
public sealed class SecurityOwnershipTests(PostgresFixture fixture) : ApiTest(fixture)
{
    private async Task<(TestLifter Owner, TestLifter Intruder, CycleResponse Cycle, WorkoutResponse Workout)>
        TwoLiftersAsync()
    {
        var owner = await RegisterLifterAsync("owner@example.com");
        var intruder = await RegisterLifterAsync("intruder@example.com");

        var cycle = await (await owner.Client.PostAsJsonAsync(
                "/api/v1/cycles", SixWeekBlockRequest.Build(), GriffGymApiFactory.Json))
            .ReadSuccessAsync<CycleResponse>();

        var workout = await (await owner.Client.PostAsJsonAsync(
                "/api/v1/workouts",
                new CreateWorkoutRequest(
                    null, cycle.Id, null, cycle.Program.Weeks[0].Workouts[0].Id,
                    null, null, null, null, null, null, null, null, null, null),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        return (owner, intruder, cycle, workout);
    }

    [Fact]
    public async Task A_lifter_cannot_read_another_lifters_workout()
    {
        var (_, intruder, _, workout) = await TwoLiftersAsync();

        var response = await intruder.Client.GetAsync($"/api/v1/workouts/{workout.Id}");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task A_lifter_cannot_read_another_lifters_cycle()
    {
        var (_, intruder, cycle, _) = await TwoLiftersAsync();

        var response = await intruder.Client.GetAsync($"/api/v1/cycles/{cycle.Id}");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task A_lifter_cannot_complete_another_lifters_workout()
    {
        var (_, intruder, _, workout) = await TwoLiftersAsync();

        var response = await intruder.Client.PostAsJsonAsync(
            $"/api/v1/workouts/{workout.Id}/complete",
            new FinishWorkoutRequest(null, null),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task A_lifter_cannot_log_a_set_into_another_lifters_workout()
    {
        var (_, intruder, _, workout) = await TwoLiftersAsync();
        var setId = workout.Exercises[0].Sets[0].Id;

        var response = await intruder.Client.PutAsJsonAsync(
            $"/api/v1/workouts/{workout.Id}/sets/{setId}",
            new LogSetRequest(null, 190m, 3, 8m, true, "not mine"),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task A_lifter_cannot_overwrite_another_lifters_workout()
    {
        var (_, intruder, _, workout) = await TwoLiftersAsync();

        var response = await intruder.Client.PutAsJsonAsync(
            $"/api/v1/workouts/{workout.Id}",
            new UpdateWorkoutRequest(null, "overwritten", null),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task A_lifter_cannot_move_another_lifters_cycle_along()
    {
        var (_, intruder, cycle, _) = await TwoLiftersAsync();

        var response = await intruder.Client.PutAsJsonAsync(
            $"/api/v1/cycles/{cycle.Id}/progress",
            new UpdateCycleProgressRequest(cycle.Program.Weeks[0].Workouts[1].Id),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task Listing_shows_only_what_belongs_to_the_caller()
    {
        var (_, intruder, _, _) = await TwoLiftersAsync();

        var cycles = await (await intruder.Client.GetAsync("/api/v1/cycles"))
            .ReadSuccessAsync<List<CycleSummaryResponse>>();

        var history = await (await intruder.Client.GetAsync("/api/v1/workouts"))
            .ReadSuccessAsync<PagedResponse<WorkoutSummaryResponse>>();

        var active = await intruder.Client.GetAsync("/api/v1/workouts/active");

        Assert.Empty(cycles);
        Assert.Empty(history.Items);
        Assert.Equal(HttpStatusCode.NoContent, active.StatusCode);
    }

    [Fact]
    public async Task The_state_document_contains_nothing_belonging_to_anybody_else()
    {
        var (owner, intruder, _, _) = await TwoLiftersAsync();

        await owner.Client.PutAsJsonAsync(
            "/api/v1/reference-maxes/Squat",
            new UpdateReferenceMaxRequest(210m, null),
            GriffGymApiFactory.Json);

        var state = await (await intruder.Client.GetAsync("/api/v1/state"))
            .ReadSuccessAsync<ApplicationStateResponse>();

        Assert.Equal(intruder.Id, state.Profile.Id);
        Assert.Empty(state.Cycles);
        Assert.Empty(state.Workouts);
        Assert.Empty(state.ReferenceMaxes);
        Assert.Empty(state.Exercises);
        Assert.Null(state.ActiveWorkoutId);
    }

    [Fact]
    public async Task A_lifter_cannot_claim_a_cycle_identifier_that_is_already_taken()
    {
        // Identifiers are global, and the answer says only that the id is in use — never whose.
        var owner = await RegisterLifterAsync("first@example.com");
        var other = await RegisterLifterAsync("second@example.com");
        var sharedId = Guid.NewGuid();

        await (await owner.Client.PostAsJsonAsync(
                "/api/v1/cycles",
                SixWeekBlockRequest.Build(sharedId),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<CycleResponse>();

        var response = await other.Client.PostAsJsonAsync(
            "/api/v1/cycles",
            SixWeekBlockRequest.Build(sharedId),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);

        var body = await response.Content.ReadAsStringAsync();
        Assert.DoesNotContain("first@example.com", body, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task Reference_maxes_are_per_lifter()
    {
        var first = await RegisterLifterAsync("a@example.com");
        var second = await RegisterLifterAsync("b@example.com");

        await first.Client.PutAsJsonAsync(
            "/api/v1/reference-maxes/Squat",
            new UpdateReferenceMaxRequest(210m, null),
            GriffGymApiFactory.Json);

        await second.Client.PutAsJsonAsync(
            "/api/v1/reference-maxes/Squat",
            new UpdateReferenceMaxRequest(140m, null),
            GriffGymApiFactory.Json);

        var firstMaxes = await (await first.Client.GetAsync("/api/v1/reference-maxes"))
            .ReadSuccessAsync<List<ReferenceMaxResponse>>();

        var max = Assert.Single(firstMaxes);
        Assert.Equal(LiftType.Squat, max.Lift);
        Assert.Equal(210m, max.ValueKg);
    }
}
