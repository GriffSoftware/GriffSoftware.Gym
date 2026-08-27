using GriffGym.Domain.Training;
using GriffGym.Infrastructure.Persistence.Repositories;
using GriffGym.TestSupport;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace GriffGym.Infrastructure.Tests;

/// <summary>
/// The rules the database itself enforces.
///
/// Worth testing separately from the domain: application code can be bypassed by a bad
/// migration, a manual fix or a future endpoint, and these constraints are the last thing
/// standing between a bug and a corrupted training history.
/// </summary>
public sealed class SchemaConstraintTests(PostgresFixture fixture) : PostgresTest(fixture)
{
    private static async Task<PostgresException> ExpectViolationAsync(Func<Task> action)
    {
        var exception = await Assert.ThrowsAsync<DbUpdateException>(action);

        return Assert.IsType<PostgresException>(exception.InnerException);
    }

    [Fact]
    public async Task One_account_per_email_address()
    {
        var email = "duplicate@example.com";

        await using (var scope = new PersistenceScope(Fixture))
        {
            scope.Users.Add(TestData.User(Guid.NewGuid(), email));
            await scope.SaveAsync();
        }

        await using (var scope = new PersistenceScope(Fixture))
        {
            scope.Users.Add(TestData.User(Guid.NewGuid(), email));

            var violation = await ExpectViolationAsync(() => scope.SaveAsync());
            Assert.Equal(PostgresErrorCodes.UniqueViolation, violation.SqlState);
        }
    }

    [Fact]
    public async Task One_reference_max_per_lift_per_lifter()
    {
        var userId = Guid.NewGuid();

        await using var scope = new PersistenceScope(Fixture);
        scope.Users.Add(TestData.User(userId));
        scope.ReferenceMaxes.Add(ReferenceMax.Create(
            Guid.NewGuid(), userId, LiftType.Squat, Weight.Of(210m), TestData.Now));
        scope.ReferenceMaxes.Add(ReferenceMax.Create(
            Guid.NewGuid(), userId, LiftType.Squat, Weight.Of(215m), TestData.Now));

        var violation = await ExpectViolationAsync(() => scope.SaveAsync());
        Assert.Equal(PostgresErrorCodes.UniqueViolation, violation.SqlState);
    }

    [Fact]
    public async Task Cycles_are_numbered_once_per_lifter()
    {
        var userId = Guid.NewGuid();
        var squatId = Guid.NewGuid();

        await using var scope = new PersistenceScope(Fixture);
        scope.Users.Add(TestData.User(userId));
        scope.Exercises.Add(TestData.Squat(userId, squatId));
        scope.Cycles.Add(TestData.Cycle(userId, squatId, cycleNumber: 3));
        scope.Cycles.Add(TestData.Cycle(userId, squatId, cycleNumber: 3));

        var violation = await ExpectViolationAsync(() => scope.SaveAsync());
        Assert.Equal(PostgresErrorCodes.UniqueViolation, violation.SqlState);
    }

    [Fact]
    public async Task Two_lifters_may_both_have_a_cycle_three()
    {
        // The uniqueness is per user, not global. Getting this backwards would mean the second
        // person to sign up could never start their third block.
        var first = Guid.NewGuid();
        var second = Guid.NewGuid();
        var firstSquat = Guid.NewGuid();
        var secondSquat = Guid.NewGuid();

        await using var scope = new PersistenceScope(Fixture);
        scope.Users.Add(TestData.User(first, "a@example.com"));
        scope.Users.Add(TestData.User(second, "b@example.com"));
        scope.Exercises.Add(TestData.Squat(first, firstSquat));
        scope.Exercises.Add(TestData.Squat(second, secondSquat));
        scope.Cycles.Add(TestData.Cycle(first, firstSquat, cycleNumber: 3));
        scope.Cycles.Add(TestData.Cycle(second, secondSquat, cycleNumber: 3));
        await scope.SaveAsync();
    }

    [Fact]
    public async Task Deleting_a_cycle_takes_its_plan_but_leaves_the_training_log()
    {
        // History outlives the plan it came from. This is the cascade rule that matters most.
        var userId = Guid.NewGuid();
        var squatId = Guid.NewGuid();
        var cycleId = Guid.NewGuid();
        var sessionId = Guid.NewGuid();

        await using (var scope = new PersistenceScope(Fixture))
        {
            scope.Users.Add(TestData.User(userId));
            scope.Exercises.Add(TestData.Squat(userId, squatId));

            var cycle = TestData.Cycle(userId, squatId, cycleId);
            scope.Cycles.Add(cycle);
            scope.Sessions.Add(TestData.StartFirstWorkout(cycle, sessionId));
            await scope.SaveAsync();
        }

        await using (var scope = new PersistenceScope(Fixture))
        {
            await scope.Context.Database.ExecuteSqlAsync(
                $"DELETE FROM training_cycle WHERE id = {cycleId}");
        }

        await using (var scope = new PersistenceScope(Fixture))
        {
            Assert.Equal(0, await Count(scope, "training_program"));
            Assert.Equal(0, await Count(scope, "training_week"));
            Assert.Equal(0, await Count(scope, "workout_template"));
            Assert.Equal(0, await Count(scope, "planned_set"));

            // The session, its exercises and its sets are all still there.
            Assert.Equal(1, await Count(scope, "workout_session"));
            Assert.Equal(2, await Count(scope, "set_log"));

            var session = await scope.Sessions
                .FindForUserAsync(userId, sessionId, default);

            Assert.NotNull(session);
            Assert.Null(session.TrainingCycleId);
            Assert.Null(session.WorkoutTemplateId);

            // The snapshot is what makes that survivable.
            Assert.Equal("Week 1 Day 1", session.Title);
            Assert.Equal(187.5m, session.Exercises[0].Sets[0].PlannedWeight!.Value.Kilograms);
        }
    }

    [Fact]
    public async Task Deleting_a_session_takes_its_logs()
    {
        var userId = Guid.NewGuid();
        var squatId = Guid.NewGuid();
        var sessionId = Guid.NewGuid();

        await using (var scope = new PersistenceScope(Fixture))
        {
            scope.Users.Add(TestData.User(userId));
            scope.Exercises.Add(TestData.Squat(userId, squatId));

            var cycle = TestData.Cycle(userId, squatId);
            scope.Cycles.Add(cycle);
            scope.Sessions.Add(TestData.StartFirstWorkout(cycle, sessionId));
            await scope.SaveAsync();
        }

        await using (var scope = new PersistenceScope(Fixture))
        {
            await scope.Context.Database.ExecuteSqlAsync(
                $"DELETE FROM workout_session WHERE id = {sessionId}");
        }

        await using (var scope = new PersistenceScope(Fixture))
        {
            Assert.Equal(0, await Count(scope, "exercise_log"));
            Assert.Equal(0, await Count(scope, "set_log"));
        }
    }

    [Fact]
    public async Task A_movement_a_plan_prescribes_cannot_be_deleted()
    {
        // Restrict rather than cascade: a plan referring to it is a reason to keep it.
        var userId = Guid.NewGuid();
        var squatId = Guid.NewGuid();

        await using (var scope = new PersistenceScope(Fixture))
        {
            scope.Users.Add(TestData.User(userId));
            scope.Exercises.Add(TestData.Squat(userId, squatId));
            scope.Cycles.Add(TestData.Cycle(userId, squatId));
            await scope.SaveAsync();
        }

        await using (var scope = new PersistenceScope(Fixture))
        {
            var exception = await Assert.ThrowsAsync<PostgresException>(() =>
                scope.Context.Database.ExecuteSqlAsync($"DELETE FROM exercise WHERE id = {squatId}"));

            Assert.Equal(PostgresErrorCodes.ForeignKeyViolation, exception.SqlState);
        }
    }

    [Fact]
    public async Task Kilograms_are_stored_as_numeric_with_two_decimals()
    {
        await using var scope = new PersistenceScope(Fixture);

        var type = await scope.Context.Database
            .SqlQuery<string>($"""
                SELECT format_type(a.atttypid, a.atttypmod) AS "Value"
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                WHERE c.relname = 'set_log' AND a.attname = 'actual_weight_kg'
                """)
            .SingleAsync();

        Assert.Equal("numeric(7,2)", type);
    }

    private static Task<int> Count(PersistenceScope scope, string table) =>
        scope.Context.Database
            .SqlQueryRaw<int>($"SELECT count(*)::int AS \"Value\" FROM {table}")
            .SingleAsync();
}
