using GriffGym.Domain.Training;
using GriffGym.Domain.Workouts;
using GriffGym.Infrastructure.Persistence.Repositories;
using GriffGym.TestSupport;
using Microsoft.EntityFrameworkCore;

namespace GriffGym.Infrastructure.Tests;

public sealed class PersistenceRoundTripTests(PostgresFixture fixture) : PostgresTest(fixture)
{
    private readonly Guid _userId = Guid.NewGuid();
    private readonly Guid _squatId = Guid.NewGuid();

    private async Task SeedUserAndCatalogueAsync()
    {
        await using var scope = new PersistenceScope(Fixture);

        scope.Users.Add(TestData.User(_userId));
        scope.Exercises.Add(TestData.Squat(_userId, _squatId));
        await scope.SaveAsync();
    }

    [Fact]
    public async Task A_cycle_survives_a_round_trip_with_its_whole_plan()
    {
        await SeedUserAndCatalogueAsync();
        var cycleId = Guid.NewGuid();

        await using (var scope = new PersistenceScope(Fixture))
        {
            scope.Cycles.Add(TestData.Cycle(_userId, _squatId, cycleId));
            await scope.SaveAsync();
        }

        await using (var scope = new PersistenceScope(Fixture))
        {
            var loaded = await scope.Cycles.FindForUserAsync(_userId, cycleId, default);

            Assert.NotNull(loaded);
            Assert.Equal(6, loaded.Program.Weeks.Count);
            Assert.Equal(18, loaded.Program.Weeks.Sum(week => week.Workouts.Count));
            Assert.Equal(
                36,
                loaded.Program.Weeks
                    .SelectMany(week => week.Workouts)
                    .SelectMany(workout => workout.Exercises)
                    .Sum(exercise => exercise.PlannedSets.Count));

            // The sequence is what "what do I train next?" reads, so its order must survive.
            Assert.Equal(
                Enumerable.Range(1, 18),
                loaded.Program.Workouts.Select(workout => workout.SequenceNumber));
        }
    }

    [Fact]
    public async Task Half_kilogram_loads_come_back_exactly()
    {
        // The reason kilograms are numeric(7,2) and not a floating point type. A load that
        // reads back as 187.49999999999997 is a corrupted training log.
        await SeedUserAndCatalogueAsync();
        var cycleId = Guid.NewGuid();

        await using (var scope = new PersistenceScope(Fixture))
        {
            scope.Cycles.Add(TestData.Cycle(_userId, _squatId, cycleId));
            await scope.SaveAsync();
        }

        await using (var scope = new PersistenceScope(Fixture))
        {
            var loaded = await scope.Cycles
                .FindForUserAsync(_userId, cycleId, default);

            var sets = loaded!.Program.Workouts.First().Exercises[0].PlannedSets;

            Assert.Equal(187.5m, sets[0].Weight!.Value.Kilograms);
            Assert.Equal(162.5m, sets[1].Weight!.Value.Kilograms);
            Assert.Equal(TestData.SquatMax, loaded.ReferenceMaxes.Squat.Kilograms);
        }
    }

    [Fact]
    public async Task An_rpe_range_survives_as_a_range()
    {
        await SeedUserAndCatalogueAsync();
        var cycleId = Guid.NewGuid();

        await using (var scope = new PersistenceScope(Fixture))
        {
            scope.Cycles.Add(TestData.Cycle(_userId, _squatId, cycleId));
            await scope.SaveAsync();
        }

        await using (var scope = new PersistenceScope(Fixture))
        {
            var loaded = await scope.Cycles
                .FindForUserAsync(_userId, cycleId, default);

            var sets = loaded!.Program.Workouts.First().Exercises[0].PlannedSets;

            Assert.False(sets[0].TargetRpe!.Value.IsRange);
            Assert.Equal(8m, sets[0].TargetRpe!.Value.Min.Value);

            Assert.True(sets[1].TargetRpe!.Value.IsRange);
            Assert.Equal(6m, sets[1].TargetRpe!.Value.Min.Value);
            Assert.Equal(7m, sets[1].TargetRpe!.Value.Max.Value);
        }
    }

    [Fact]
    public async Task A_workout_keeps_planned_and_actual_apart_across_a_round_trip()
    {
        await SeedUserAndCatalogueAsync();
        var cycleId = Guid.NewGuid();
        var sessionId = Guid.NewGuid();
        Guid setId;

        await using (var scope = new PersistenceScope(Fixture))
        {
            var cycle = TestData.Cycle(_userId, _squatId, cycleId);
            scope.Cycles.Add(cycle);

            var session = TestData.StartFirstWorkout(cycle, sessionId);
            setId = session.Exercises[0].Sets[0].Id;

            session.LogSet(
                setId,
                new SetResult(Weight.Of(190m), 2, Rpe.Of(9.5m), Completed: true, "grinder"),
                TestData.Now.AddMinutes(5));

            scope.Sessions.Add(session);
            await scope.SaveAsync();
        }

        await using (var scope = new PersistenceScope(Fixture))
        {
            var loaded = await scope.Sessions
                .FindForUserAsync(_userId, sessionId, default);

            var set = loaded!.Exercises[0].Sets.Single(candidate => candidate.Id == setId);

            // What was asked for.
            Assert.Equal(187.5m, set.PlannedWeight!.Value.Kilograms);
            Assert.Equal(3, set.PlannedReps);
            Assert.Equal(8m, set.PlannedRpe!.Value.Min.Value);

            // What happened.
            Assert.Equal(190m, set.ActualWeight!.Value.Kilograms);
            Assert.Equal(2, set.ActualReps);
            Assert.Equal(9.5m, set.ActualRpe!.Value.Value);
            Assert.True(set.Completed);
            Assert.Equal("grinder", set.Notes);
        }
    }

    [Fact]
    public async Task Timestamps_come_back_as_utc()
    {
        await SeedUserAndCatalogueAsync();

        await using var scope = new PersistenceScope(Fixture);
        var user = await scope.Users.FindByIdAsync(_userId, default);

        Assert.NotNull(user);
        Assert.Equal(TimeSpan.Zero, user.CreatedAtUtc.Offset);
    }

    [Fact]
    public async Task A_workout_written_offline_and_uploaded_whole_replaces_only_its_own_sets()
    {
        await SeedUserAndCatalogueAsync();
        var cycleId = Guid.NewGuid();
        var sessionId = Guid.NewGuid();

        await using (var scope = new PersistenceScope(Fixture))
        {
            var cycle = TestData.Cycle(_userId, _squatId, cycleId);
            scope.Cycles.Add(cycle);
            scope.Sessions.Add(TestData.StartFirstWorkout(cycle, sessionId));
            await scope.SaveAsync();
        }

        Guid keptSetId;

        await using (var scope = new PersistenceScope(Fixture))
        {
            var session = await scope.Sessions.FindForUserAsync(_userId, sessionId, default);
            var exercise = session!.Exercises[0];
            keptSetId = exercise.Sets[0].Id;

            // The phone sends the tree it holds: one set instead of two.
            session.ReplaceExercises(
                [
                    ExerciseLog.Create(
                        exercise.Id,
                        exercise.Position,
                        exercise.ExerciseId,
                        exercise.ExerciseName,
                        exercise.ExerciseCategory,
                        exercise.Type,
                        null,
                        [
                            SetLog.Create(
                                keptSetId,
                                1,
                                Weight.Of(187.5m),
                                3,
                                RpeTarget.Exact(8m),
                                new SetResult(Weight.Of(187.5m), 3, Rpe.Of(8m), true, null)),
                        ]),
                ],
                TestData.Now.AddMinutes(30));
            await scope.SaveAsync();
        }

        await using (var scope = new PersistenceScope(Fixture))
        {
            var session = await scope.Sessions
                .FindForUserAsync(_userId, sessionId, default);

            var set = Assert.Single(session!.Exercises[0].Sets);
            Assert.Equal(keptSetId, set.Id);

            // The dropped row is gone from the table, not merely detached.
            Assert.Equal(1, await scope.Context.Database
                .SqlQuery<int>($"SELECT count(*)::int AS \"Value\" FROM set_log")
                .SingleAsync());
        }
    }
}
