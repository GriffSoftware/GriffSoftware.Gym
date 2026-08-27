using System.Net;
using System.Net.Http.Json;
using GriffGym.Api.Contracts.V1;
using GriffGym.Domain.Training;
using GriffGym.TestSupport;

namespace GriffGym.Api.IntegrationTests;

public sealed class WorkoutEndpointTests(PostgresFixture fixture) : ApiTest(fixture)
{
    private async Task<(TestLifter Lifter, CycleResponse Cycle)> WithCycleAsync()
    {
        var lifter = await RegisterLifterAsync();

        var cycle = await (await lifter.Client.PostAsJsonAsync(
                "/api/v1/cycles", SixWeekBlockRequest.Build(), GriffGymApiFactory.Json))
            .ReadSuccessAsync<CycleResponse>();

        return (lifter, cycle);
    }

    private static CreateWorkoutRequest StartOf(CycleResponse cycle, int day = 0, Guid? id = null) =>
        new(
            id, cycle.Id, null, cycle.Program.Weeks[0].Workouts[day].Id,
            null, null, null, null, null, null, null, null, null, null);

    [Fact]
    public async Task Creating_a_cycle_stores_the_whole_plan()
    {
        var lifter = await RegisterLifterAsync();

        var response = await lifter.Client.PostAsJsonAsync(
            "/api/v1/cycles", SixWeekBlockRequest.Build(), GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Created, response.StatusCode);

        var cycle = await response.ReadAsync<CycleResponse>();
        Assert.Equal(6, cycle.Program.Weeks.Count);
        Assert.Equal(18, cycle.Program.Weeks.Sum(week => week.Workouts.Count));
        Assert.Equal(210m, cycle.ReferenceMaxes.SquatKg);
        Assert.Equal(TrainingCycleStatus.Active, cycle.Status);

        // A brand new cycle points at its first unit.
        Assert.Equal(cycle.Program.Weeks[0].Workouts[0].Id, cycle.Program.CurrentWorkoutTemplateId);
    }

    [Fact]
    public async Task Posting_the_same_cycle_twice_returns_the_one_that_exists()
    {
        // A phone that retried after a timeout must not end up with two blocks.
        var lifter = await RegisterLifterAsync();
        var cycleId = Guid.NewGuid();
        var payload = SixWeekBlockRequest.Build(cycleId);

        var first = await lifter.Client.PostAsJsonAsync(
            "/api/v1/cycles", payload, GriffGymApiFactory.Json);
        var second = await lifter.Client.PostAsJsonAsync(
            "/api/v1/cycles", payload, GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Created, first.StatusCode);
        Assert.Equal(HttpStatusCode.OK, second.StatusCode);

        var cycles = await (await lifter.Client.GetAsync("/api/v1/cycles"))
            .ReadSuccessAsync<List<CycleSummaryResponse>>();

        Assert.Single(cycles);
    }

    [Fact]
    public async Task Starting_a_workout_snapshots_the_planned_unit()
    {
        var (lifter, cycle) = await WithCycleAsync();
        var template = cycle.Program.Weeks[0].Workouts[0];

        var response = await lifter.Client.PostAsJsonAsync(
            "/api/v1/workouts", StartOf(cycle), GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Created, response.StatusCode);

        var workout = await response.ReadAsync<WorkoutResponse>();
        Assert.Equal(WorkoutSessionStatus.InProgress, workout.Status);
        Assert.Equal(template.Title, workout.Title);
        Assert.Equal(2, workout.Exercises.Count);
        Assert.Equal(187.5m, workout.Exercises[0].Sets[0].PlannedWeightKg);
        Assert.All(workout.Exercises.SelectMany(e => e.Sets), set => Assert.Null(set.ActualWeightKg));
    }

    [Fact]
    public async Task The_active_workout_is_returned_and_204_when_there_is_none()
    {
        var (lifter, cycle) = await WithCycleAsync();

        Assert.Equal(
            HttpStatusCode.NoContent,
            (await lifter.Client.GetAsync("/api/v1/workouts/active")).StatusCode);

        var started = await (await lifter.Client.PostAsJsonAsync(
                "/api/v1/workouts", StartOf(cycle), GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        var active = await (await lifter.Client.GetAsync("/api/v1/workouts/active"))
            .ReadSuccessAsync<WorkoutResponse>();

        Assert.Equal(started.Id, active.Id);
    }

    [Fact]
    public async Task Logging_a_set_records_the_result_beside_the_plan()
    {
        var (lifter, cycle) = await WithCycleAsync();
        var workout = await (await lifter.Client.PostAsJsonAsync(
                "/api/v1/workouts", StartOf(cycle), GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        var setId = workout.Exercises[0].Sets[0].Id;

        var updated = await (await lifter.Client.PutAsJsonAsync(
                $"/api/v1/workouts/{workout.Id}/sets/{setId}",
                new LogSetRequest(null, 190m, 3, 8.5m, true, "solid"),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        var set = updated.Exercises[0].Sets.Single(candidate => candidate.Id == setId);

        Assert.Equal(187.5m, set.PlannedWeightKg);
        Assert.Equal(190m, set.ActualWeightKg);
        Assert.Equal(8.5m, set.ActualRpe);
        Assert.True(set.Completed);
        Assert.Equal(570m, set.VolumeKg);
    }

    [Fact]
    public async Task Completing_a_workout_freezes_its_tonnage_and_locks_it()
    {
        var (lifter, cycle) = await WithCycleAsync();
        var workout = await (await lifter.Client.PostAsJsonAsync(
                "/api/v1/workouts", StartOf(cycle), GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        var setId = workout.Exercises[0].Sets[0].Id;

        await lifter.Client.PutAsJsonAsync(
            $"/api/v1/workouts/{workout.Id}/sets/{setId}",
            new LogSetRequest(null, 190m, 3, 8m, true, null),
            GriffGymApiFactory.Json);

        var completed = await (await lifter.Client.PostAsJsonAsync(
                $"/api/v1/workouts/{workout.Id}/complete",
                new FinishWorkoutRequest(null, null),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        Assert.Equal(WorkoutSessionStatus.Completed, completed.Status);
        Assert.Equal(570m, completed.TotalVolumeKg);
        Assert.NotNull(completed.FinishedAtUtc);

        // History is history: a finished workout stops accepting writes.
        var late = await lifter.Client.PutAsJsonAsync(
            $"/api/v1/workouts/{workout.Id}/sets/{setId}",
            new LogSetRequest(null, 200m, 3, 8m, true, "revisionism"),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.UnprocessableEntity, late.StatusCode);
    }

    [Fact]
    public async Task A_second_workout_cannot_start_while_one_is_running()
    {
        var (lifter, cycle) = await WithCycleAsync();
        await lifter.Client.PostAsJsonAsync(
            "/api/v1/workouts", StartOf(cycle), GriffGymApiFactory.Json);

        var response = await lifter.Client.PostAsJsonAsync(
            "/api/v1/workouts", StartOf(cycle, day: 1), GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
    }

    [Fact]
    public async Task A_stale_expected_version_is_refused_with_the_current_one()
    {
        var (lifter, cycle) = await WithCycleAsync();
        var workout = await (await lifter.Client.PostAsJsonAsync(
                "/api/v1/workouts", StartOf(cycle), GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        var response = await lifter.Client.PutAsJsonAsync(
            $"/api/v1/workouts/{workout.Id}/sets/{workout.Exercises[0].Sets[0].Id}",
            new LogSetRequest(ExpectedVersion: 99, 190m, 3, 8m, true, null),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);

        var problem = await response.Content.ReadAsStringAsync();
        Assert.Contains("expectedVersion", problem, StringComparison.Ordinal);
        Assert.Contains("actualVersion", problem, StringComparison.Ordinal);
    }

    [Fact]
    public async Task The_version_a_write_returns_is_the_one_the_next_write_should_send()
    {
        var (lifter, cycle) = await WithCycleAsync();
        var workout = await (await lifter.Client.PostAsJsonAsync(
                "/api/v1/workouts", StartOf(cycle), GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        var setId = workout.Exercises[0].Sets[0].Id;

        var afterFirst = await (await lifter.Client.PutAsJsonAsync(
                $"/api/v1/workouts/{workout.Id}/sets/{setId}",
                new LogSetRequest(workout.Version, 190m, 3, 8m, true, null),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        Assert.True(afterFirst.Version > workout.Version);

        // Round-tripping the version it was just handed must succeed, or optimistic concurrency
        // would make every second write fail.
        var afterSecond = await lifter.Client.PutAsJsonAsync(
            $"/api/v1/workouts/{workout.Id}/sets/{setId}",
            new LogSetRequest(afterFirst.Version, 192.5m, 3, 8.5m, true, null),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.OK, afterSecond.StatusCode);
    }

    [Fact]
    public async Task Completing_a_set_without_saying_what_was_lifted_is_rejected()
    {
        var (lifter, cycle) = await WithCycleAsync();
        var workout = await (await lifter.Client.PostAsJsonAsync(
                "/api/v1/workouts", StartOf(cycle), GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        var response = await lifter.Client.PutAsJsonAsync(
            $"/api/v1/workouts/{workout.Id}/sets/{workout.Exercises[0].Sets[0].Id}",
            new LogSetRequest(null, null, null, null, Completed: true, null),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Theory]
    [InlineData(10.5)]
    [InlineData(7.31)]
    [InlineData(0.5)]
    public async Task An_impossible_rpe_is_rejected(decimal rpe)
    {
        var (lifter, cycle) = await WithCycleAsync();
        var workout = await (await lifter.Client.PostAsJsonAsync(
                "/api/v1/workouts", StartOf(cycle), GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        var response = await lifter.Client.PutAsJsonAsync(
            $"/api/v1/workouts/{workout.Id}/sets/{workout.Exercises[0].Sets[0].Id}",
            new LogSetRequest(null, 190m, 3, rpe, true, null),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task History_is_paged_newest_first_and_can_be_filtered_by_cycle()
    {
        var (lifter, cycle) = await WithCycleAsync();

        for (var day = 0; day < 3; day++)
        {
            var workout = await (await lifter.Client.PostAsJsonAsync(
                    "/api/v1/workouts", StartOf(cycle, day), GriffGymApiFactory.Json))
                .ReadSuccessAsync<WorkoutResponse>();

            await lifter.Client.PostAsJsonAsync(
                $"/api/v1/workouts/{workout.Id}/complete",
                new FinishWorkoutRequest(null, null),
                GriffGymApiFactory.Json);
        }

        var page = await (await lifter.Client.GetAsync("/api/v1/workouts?page=1&pageSize=2"))
            .ReadSuccessAsync<PagedResponse<WorkoutSummaryResponse>>();

        Assert.Equal(3, page.TotalCount);
        Assert.Equal(2, page.Items.Count);
        Assert.True(page.HasNextPage);

        var filtered = await (await lifter.Client.GetAsync(
                $"/api/v1/workouts?cycleId={cycle.Id}&status=Completed"))
            .ReadSuccessAsync<PagedResponse<WorkoutSummaryResponse>>();

        Assert.Equal(3, filtered.TotalCount);

        var empty = await (await lifter.Client.GetAsync(
                $"/api/v1/workouts?cycleId={Guid.NewGuid()}"))
            .ReadSuccessAsync<PagedResponse<WorkoutSummaryResponse>>();

        Assert.Empty(empty.Items);
    }

    [Fact]
    public async Task A_cancelled_workout_is_finished_without_being_completed()
    {
        var (lifter, cycle) = await WithCycleAsync();
        var workout = await (await lifter.Client.PostAsJsonAsync(
                "/api/v1/workouts", StartOf(cycle), GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        var cancelled = await (await lifter.Client.PostAsJsonAsync(
                $"/api/v1/workouts/{workout.Id}/cancel",
                new FinishWorkoutRequest(null, null),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<WorkoutResponse>();

        Assert.Equal(WorkoutSessionStatus.Cancelled, cancelled.Status);
        Assert.Equal(
            HttpStatusCode.NoContent,
            (await lifter.Client.GetAsync("/api/v1/workouts/active")).StatusCode);
    }

    [Fact]
    public async Task Completing_a_cycle_closes_it_and_clears_its_pointer()
    {
        var (lifter, cycle) = await WithCycleAsync();

        var completed = await (await lifter.Client.PostAsJsonAsync(
                $"/api/v1/cycles/{cycle.Id}/complete",
                new CompleteCycleRequest(null),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<CycleResponse>();

        Assert.Equal(TrainingCycleStatus.Completed, completed.Status);
        Assert.NotNull(completed.CompletedAtUtc);
        Assert.Null(completed.Program.CurrentWorkoutTemplateId);

        var again = await lifter.Client.PostAsJsonAsync(
            $"/api/v1/cycles/{cycle.Id}/complete",
            new CompleteCycleRequest(null),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.UnprocessableEntity, again.StatusCode);
    }

    [Fact]
    public async Task Changing_a_reference_max_leaves_a_planned_cycle_alone()
    {
        var (lifter, cycle) = await WithCycleAsync();

        await lifter.Client.PutAsJsonAsync(
            "/api/v1/reference-maxes/Squat",
            new UpdateReferenceMaxRequest(999m, null),
            GriffGymApiFactory.Json);

        var reloaded = await (await lifter.Client.GetAsync($"/api/v1/cycles/{cycle.Id}"))
            .ReadSuccessAsync<CycleResponse>();

        Assert.Equal(210m, reloaded.ReferenceMaxes.SquatKg);
        Assert.Equal(187.5m, reloaded.Program.Weeks[0].Workouts[0].Exercises[0].PlannedSets[0].WeightKg);
    }

    [Fact]
    public async Task Health_endpoints_answer_without_credentials()
    {
        var client = CreateClient();

        Assert.Equal(HttpStatusCode.OK, (await client.GetAsync("/health/live")).StatusCode);
        Assert.Equal(HttpStatusCode.OK, (await client.GetAsync("/health/ready")).StatusCode);
        Assert.Equal(HttpStatusCode.OK, (await client.GetAsync("/health")).StatusCode);
    }
}
