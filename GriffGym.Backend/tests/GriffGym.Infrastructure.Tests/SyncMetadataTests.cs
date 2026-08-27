using GriffGym.Application.Common;
using GriffGym.Domain.Training;
using GriffGym.Domain.Workouts;
using GriffGym.Infrastructure.Persistence.Repositories;
using GriffGym.TestSupport;
using Microsoft.EntityFrameworkCore;

namespace GriffGym.Infrastructure.Tests;

/// <summary>
/// The machinery a future offline sync will stand on: a revision counter that makes a lost
/// update detectable, and a monotonic cursor that makes "what changed since?" answerable.
/// </summary>
public sealed class SyncMetadataTests(PostgresFixture fixture) : PostgresTest(fixture)
{
    private readonly Guid _userId = Guid.NewGuid();
    private readonly Guid _squatId = Guid.NewGuid();

    private async Task SeedAsync()
    {
        await using var scope = new PersistenceScope(Fixture);
        scope.Users.Add(TestData.User(_userId));
        scope.Exercises.Add(TestData.Squat(_userId, _squatId));
        await scope.SaveAsync();
    }

    [Fact]
    public async Task A_new_row_starts_at_version_one()
    {
        await SeedAsync();

        await using var scope = new PersistenceScope(Fixture);
        var max = await scope.ReferenceMaxes
            .FindForUserAsync(_userId, LiftType.Squat, default);

        Assert.Null(max);

        scope.ReferenceMaxes.Add(ReferenceMax.Create(
            Guid.NewGuid(), _userId, LiftType.Squat, Weight.Of(210m), TestData.Now));
        await scope.SaveAsync();

        var stored = await scope.ReferenceMaxes
            .FindForUserAsync(_userId, LiftType.Squat, default);

        Assert.Equal(1, stored!.Version);
        Assert.True(stored.SyncVersion > 0);
    }

    [Fact]
    public async Task Every_update_moves_the_version_on()
    {
        await SeedAsync();
        var maxId = Guid.NewGuid();

        await using (var scope = new PersistenceScope(Fixture))
        {
            scope.ReferenceMaxes.Add(ReferenceMax.Create(
                maxId, _userId, LiftType.Squat, Weight.Of(210m), TestData.Now));
            await scope.SaveAsync();
        }

        await using (var scope = new PersistenceScope(Fixture))
        {
            var stored = await scope.ReferenceMaxes.FindForUserAsync(_userId, LiftType.Squat, default);
            stored!.UpdateValue(Weight.Of(215m), TestData.Now.AddDays(1));
            await scope.SaveAsync();
        }

        await using (var scope = new PersistenceScope(Fixture))
        {
            var stored = await scope.ReferenceMaxes
                .FindForUserAsync(_userId, LiftType.Squat, default);

            Assert.Equal(2, stored!.Version);
            Assert.Equal(215m, stored.Value.Kilograms);
        }
    }

    [Fact]
    public async Task Everything_written_in_one_save_shares_a_sync_version()
    {
        // So a delta query returns whole transactions, never half a cycle and half its program.
        await SeedAsync();

        await using var scope = new PersistenceScope(Fixture);
        scope.Cycles.Add(TestData.Cycle(_userId, _squatId));
        scope.ReferenceMaxes.Add(ReferenceMax.Create(
            Guid.NewGuid(), _userId, LiftType.Squat, Weight.Of(210m), TestData.Now));
        await scope.SaveAsync();

        var versions = await scope.Context.Database
            .SqlQuery<long>($"""
                SELECT sync_version AS "Value" FROM training_cycle
                UNION
                SELECT sync_version FROM reference_max
                """)
            .ToListAsync();

        Assert.Single(versions);
    }

    [Fact]
    public async Task The_sync_cursor_only_ever_moves_forward()
    {
        await SeedAsync();

        long first;
        long second;

        await using (var scope = new PersistenceScope(Fixture))
        {
            scope.ReferenceMaxes.Add(ReferenceMax.Create(
                Guid.NewGuid(), _userId, LiftType.Squat, Weight.Of(210m), TestData.Now));
            await scope.SaveAsync();

            first = (await scope.ReferenceMaxes.FindForUserAsync(_userId, LiftType.Squat, default))!.SyncVersion;
        }

        await using (var scope = new PersistenceScope(Fixture))
        {
            scope.ReferenceMaxes.Add(ReferenceMax.Create(
                Guid.NewGuid(), _userId, LiftType.Deadlift, Weight.Of(225m), TestData.Now));
            await scope.SaveAsync();

            second = (await scope.ReferenceMaxes.FindForUserAsync(_userId, LiftType.Deadlift, default))!
                .SyncVersion;
        }

        Assert.True(second > first, $"expected {second} to be above {first}");
    }

    [Fact]
    public async Task Two_devices_writing_the_same_workout_do_not_silently_lose_one()
    {
        // The database is the last line of defence here. The application checks the version it
        // was told about; this checks that the UPDATE itself matches on the revision it read,
        // so even a racing write that slipped past that check cannot overwrite blindly.
        await SeedAsync();
        var cycleId = Guid.NewGuid();
        var sessionId = Guid.NewGuid();

        await using (var scope = new PersistenceScope(Fixture))
        {
            var cycle = TestData.Cycle(_userId, _squatId, cycleId);
            scope.Cycles.Add(cycle);
            scope.Sessions.Add(TestData.StartFirstWorkout(cycle, sessionId));
            await scope.SaveAsync();
        }

        await using var phoneA = new PersistenceScope(Fixture);
        await using var phoneB = new PersistenceScope(Fixture);

        var repositoryA = phoneA.Sessions;
        var repositoryB = phoneB.Sessions;

        var sessionA = await repositoryA.FindForUserAsync(_userId, sessionId, default);
        var sessionB = await repositoryB.FindForUserAsync(_userId, sessionId, default);

        sessionA!.LogSet(
            sessionA.Exercises[0].Sets[0].Id,
            new SetResult(Weight.Of(190m), 3, Rpe.Of(8m), true, "phone A"),
            TestData.Now.AddMinutes(10));
        await phoneA.SaveAsync();

        sessionB!.LogSet(
            sessionB.Exercises[0].Sets[0].Id,
            new SetResult(Weight.Of(175m), 5, Rpe.Of(7m), true, "phone B"),
            TestData.Now.AddMinutes(11));

        await Assert.ThrowsAsync<ConflictException>(() => phoneB.SaveAsync());

        await using var verification = new PersistenceScope(Fixture);
        var stored = await verification.Sessions
            .FindForUserAsync(_userId, sessionId, default);

        Assert.Equal("phone A", stored!.Exercises[0].Sets[0].Notes);
    }

    [Fact]
    public async Task Every_synchronised_table_carries_a_tombstone_column()
    {
        // Phase 1 never deletes. The column exists so that when delta sync arrives, a removal
        // can be told to a device that was offline when it happened — a hard delete cannot.
        await using var scope = new PersistenceScope(Fixture);

        var tables = await scope.Context.Database
            .SqlQuery<string>($"""
                SELECT table_name AS "Value"
                FROM information_schema.columns
                WHERE table_schema = 'public' AND column_name = 'deleted_at_utc'
                ORDER BY table_name
                """)
            .ToListAsync();

        Assert.Equal(
            ["exercise", "reference_max", "training_cycle", "user", "workout_session"],
            tables);
    }
}
