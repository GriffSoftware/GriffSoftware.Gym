using GriffGym.TestSupport;

namespace GriffGym.Api.IntegrationTests;

[CollectionDefinition(Name)]
public sealed class ApiCollection : ICollectionFixture<PostgresFixture>
{
    public const string Name = "api";
}

/// <summary>
/// Base for the API tests: one database and one host for the assembly, emptied between tests.
///
/// Skips rather than fails when no PostgreSQL is reachable, so "no database on this laptop"
/// never looks like "the API is broken".
/// </summary>
[Collection(ApiCollection.Name)]
public abstract class ApiTest(PostgresFixture fixture) : IAsyncLifetime
{
    private GriffGymApiFactory? _factory;

    protected PostgresFixture Fixture { get; } = fixture;

    protected GriffGymApiFactory Factory =>
        _factory ?? throw new InvalidOperationException("The host has not been started.");

    public async ValueTask InitializeAsync()
    {
        Assert.SkipUnless(Fixture.IsAvailable, Fixture.SkipReason);

        await Fixture.ResetAsync();
        _factory = new GriffGymApiFactory(Fixture.ConnectionString);
    }

    /// <summary>A client with no credentials. Everything but the auth endpoints should refuse it.</summary>
    protected HttpClient CreateClient() => Factory.CreateClient();

    /// <summary>Registers a lifter and returns a client already carrying their access token.</summary>
    protected Task<TestLifter> RegisterLifterAsync(string? email = null) =>
        TestLifter.RegisterAsync(Factory, email);

    public async ValueTask DisposeAsync()
    {
        GC.SuppressFinalize(this);

        if (_factory is not null)
        {
            await _factory.DisposeAsync();
        }
    }
}
