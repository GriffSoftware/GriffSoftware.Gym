using GriffGym.TestSupport;

namespace GriffGym.Infrastructure.Tests;

/// <summary>
/// One database for the whole assembly, created once and truncated between tests. Migrating a
/// fresh database per test would dominate the runtime for no extra confidence.
/// </summary>
[CollectionDefinition(Name)]
public sealed class PostgresCollection : ICollectionFixture<PostgresFixture>
{
    public const string Name = "postgres";
}

/// <summary>
/// Base for tests that need real PostgreSQL.
///
/// Skips rather than fails when none is reachable: a red suite that only means "no database on
/// this machine" trains people to ignore red suites.
/// </summary>
[Collection(PostgresCollection.Name)]
public abstract class PostgresTest(PostgresFixture fixture) : IAsyncLifetime
{
    protected PostgresFixture Fixture { get; } = fixture;

    public async ValueTask InitializeAsync()
    {
        Assert.SkipUnless(Fixture.IsAvailable, Fixture.SkipReason);

        await Fixture.ResetAsync();
    }

    public ValueTask DisposeAsync()
    {
        GC.SuppressFinalize(this);
        return ValueTask.CompletedTask;
    }
}
