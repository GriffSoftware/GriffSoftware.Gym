using GriffGym.Infrastructure.Persistence.Entities;
using GriffGym.TestSupport;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace GriffGym.Infrastructure.Tests;

/// <summary>
/// Account deletion is the one operation in this system that destroys data on purpose, so the
/// only two acceptable outcomes are everything gone and nothing gone.
///
/// The interesting one is the second. A partial deletion is not a smaller version of a
/// successful one — it is a lifter whose training log has been erased while their account, and
/// the impression that their data is safe, both remain. These tests drive the real
/// <c>UnitOfWork</c> against real PostgreSQL, because a transaction is precisely the thing a
/// fake cannot stand in for.
/// </summary>
public sealed class AccountDeletionTransactionTests(PostgresFixture fixture) : PostgresTest(fixture)
{
    private readonly Guid _userId = Guid.NewGuid();
    private readonly Guid _squatId = Guid.NewGuid();
    private readonly Guid _cycleId = Guid.NewGuid();

    [Fact]
    public async Task A_failure_part_way_through_leaves_the_account_exactly_as_it_was()
    {
        await SeedAWholeLifterAsync();
        var before = await CountAsync();

        await using var scope = new PersistenceScope(Fixture);

        var boom = await Assert.ThrowsAsync<InvalidOperationException>(() =>
            scope.UnitOfWork.ExecuteInTransactionAsync<int>(
                async token =>
                {
                    // The real first two steps of the deletion path...
                    await scope.Sessions.DeleteAllForUserAsync(_userId, token);
                    await scope.Cycles.DeleteAllForUserAsync(_userId, token);

                    // ...and then whatever goes wrong next. A dropped connection, a constraint
                    // nobody anticipated, a deploy mid-request: the cause does not matter, only
                    // that the rows already deleted come back.
                    throw new InvalidOperationException("the database went away");
                },
                default));

        Assert.Equal("the database went away", boom.Message);

        Assert.Equal(before, await CountAsync());
    }

    [Fact]
    public async Task The_ordered_deletion_empties_every_table_the_lifter_owned()
    {
        await SeedAWholeLifterAsync();

        await using (var scope = new PersistenceScope(Fixture))
        {
            await scope.UnitOfWork.ExecuteInTransactionAsync(
                async token =>
                {
                    await scope.Sessions.DeleteAllForUserAsync(_userId, token);
                    await scope.Cycles.DeleteAllForUserAsync(_userId, token);
                    await scope.Exercises.DeleteAllForUserAsync(_userId, token);
                    await scope.ReferenceMaxes.DeleteAllForUserAsync(_userId, token);
                    await scope.RefreshTokens.DeleteAllForUserAsync(_userId, token);
                    return await scope.Users.DeleteAsync(_userId, token);
                },
                default);
        }

        Assert.Equal(new OwnedRows(0, 0, 0, 0, 0, 0, 0), await CountAsync());
    }

    /// <summary>
    /// The reason the deletion path deletes cycles before the movement catalogue, written down
    /// as a test rather than as a comment somebody can delete.
    ///
    /// <c>exercise_template</c> holds a <c>RESTRICT</c> reference to <c>exercise</c>: a plan
    /// that prescribes a movement is a reason not to delete that movement. Anybody who
    /// "simplifies" the order, or replaces the whole thing with a single delete of the
    /// <c>user</c> row and lets the two sibling cascade paths race, finds out here.
    /// </summary>
    [Fact]
    public async Task Deleting_the_catalogue_before_the_plans_is_refused_by_the_database()
    {
        await SeedAWholeLifterAsync();

        await using var scope = new PersistenceScope(Fixture);

        var refused = await Assert.ThrowsAsync<PostgresException>(() =>
            scope.Exercises.DeleteAllForUserAsync(_userId, default));

        // 23503 is foreign_key_violation. Asserted by code rather than by message so the test
        // does not depend on PostgreSQL's wording or on the server's locale.
        Assert.Equal("23503", refused.SqlState);
        Assert.Equal("exercise_template", refused.TableName);
    }

    private async Task SeedAWholeLifterAsync()
    {
        await using var scope = new PersistenceScope(Fixture);

        scope.Users.Add(TestData.User(_userId));
        scope.Exercises.Add(TestData.Squat(_userId, _squatId));
        await scope.SaveAsync();

        var cycle = TestData.Cycle(_userId, _squatId, _cycleId);
        scope.Cycles.Add(cycle);
        scope.Sessions.Add(TestData.StartFirstWorkout(cycle));
        await scope.SaveAsync();

        // A live session on a device, so refresh tokens are part of what has to go.
        await using var raw = Fixture.CreateContext();
        raw.Set<RefreshTokenRecord>().Add(new RefreshTokenRecord
        {
            Id = Guid.CreateVersion7(),
            UserId = _userId,
            TokenHash = $"sha256:{Guid.NewGuid():N}",
            DeviceId = "pixel-9",
            ExpiresAtUtc = TestData.Now.AddDays(30),
            CreatedAtUtc = TestData.Now,
            UpdatedAtUtc = TestData.Now,
        });
        await raw.SaveChangesAsync();
    }

    private async Task<OwnedRows> CountAsync()
    {
        await using var context = Fixture.CreateContext();

        return new OwnedRows(
            await context.Set<UserRecord>().CountAsync(row => row.Id == _userId),
            await context.Set<ExerciseRecord>().CountAsync(row => row.UserId == _userId),
            await context.Set<TrainingCycleRecord>().CountAsync(row => row.UserId == _userId),
            await context.Set<PlannedSetRecord>().CountAsync(row =>
                row.ExerciseTemplate!.Workout!.Week!.Program!.Cycle!.UserId == _userId),
            await context.Set<WorkoutSessionRecord>().CountAsync(row => row.UserId == _userId),
            await context.Set<SetLogRecord>().CountAsync(row =>
                row.ExerciseLog!.Session!.UserId == _userId),
            await context.Set<RefreshTokenRecord>().CountAsync(row => row.UserId == _userId));
    }

    private sealed record OwnedRows(
        int Users,
        int Exercises,
        int Cycles,
        int PlannedSets,
        int Sessions,
        int SetLogs,
        int RefreshTokens);
}
