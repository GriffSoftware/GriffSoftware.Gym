using GriffGym.Application.Abstractions;
using GriffGym.Infrastructure.Persistence;
using GriffGym.Infrastructure.Persistence.Interceptors;
using GriffGym.Infrastructure.Time;
using Microsoft.EntityFrameworkCore;
using Npgsql;
using Testcontainers.PostgreSql;
using Xunit;

namespace GriffGym.TestSupport;

/// <summary>
/// A real PostgreSQL database for tests that are about persistence.
///
/// Real, not the EF in-memory provider. That provider is a LINQ shim over a dictionary: it has
/// no unique indexes, no foreign keys, no cascade rules, no <c>numeric(7,2)</c> and no
/// concurrency tokens — in other words it silently passes exactly the tests worth writing here.
///
/// Where the database comes from is resolved in order, so this works on a laptop and in CI
/// without either having to be configured for the other:
///
///   1. <c>GRIFFGYM_TEST_POSTGRES</c>, if it is set;
///   2. a Testcontainers instance, when a Docker daemon is reachable;
///   3. a PostgreSQL already running locally, over TCP or a Unix socket.
///
/// If none of the three answers, <see cref="IsAvailable"/> stays false and the tests that need
/// a database skip with a reason rather than failing. A red suite that only means "no Postgres
/// here" teaches people to ignore red suites.
/// </summary>
public sealed class PostgresFixture : IAsyncLifetime
{
    private const string EnvironmentVariable = "GRIFFGYM_TEST_POSTGRES";

    private PostgreSqlContainer? _container;
    private string? _adminConnectionString;
    private string? _databaseName;

    public bool IsAvailable { get; private set; }

    public string SkipReason { get; private set; } =
        $"No PostgreSQL is reachable. Set {EnvironmentVariable}, or start Docker, or run a local server.";

    public string ConnectionString { get; private set; } = string.Empty;

    public async ValueTask InitializeAsync()
    {
        _adminConnectionString = await ResolveAdminConnectionStringAsync();

        if (_adminConnectionString is null)
        {
            return;
        }

        // One disposable database per fixture, so a run cannot inherit rows from the last one
        // and two runs cannot collide.
        _databaseName = $"griffgym_test_{Guid.NewGuid():N}";

        await ExecuteOnAdminAsync($"CREATE DATABASE \"{_databaseName}\"");

        ConnectionString = new NpgsqlConnectionStringBuilder(_adminConnectionString)
        {
            Database = _databaseName,
        }.ConnectionString;

        await using var context = CreateContext();
        await context.Database.MigrateAsync();

        IsAvailable = true;
    }

    /// <summary>
    /// A context wired exactly as the application's is, interceptor included — otherwise the
    /// tests would be exercising a different persistence stack from the one that ships.
    /// </summary>
    public GriffGymDbContext CreateContext(IClock? clock = null) =>
        new(new DbContextOptionsBuilder<GriffGymDbContext>()
            .UseNpgsql(ConnectionString)
            .AddInterceptors(new SyncMetadataInterceptor(clock ?? new SystemClock()))
            .Options);

    /// <summary>
    /// Empties every table between tests. Truncating is far cheaper than migrating a new
    /// database each time, and restarting the identity keeps sync versions predictable.
    /// </summary>
    public async Task ResetAsync()
    {
        if (!IsAvailable)
        {
            return;
        }

        await using var connection = new NpgsqlConnection(ConnectionString);
        await connection.OpenAsync();

        await using var command = connection.CreateCommand();
        command.CommandText =
            """
            DO $$
            DECLARE statement text;
            BEGIN
              SELECT 'TRUNCATE TABLE ' || string_agg(format('%I.%I', schemaname, tablename), ', ')
                     || ' RESTART IDENTITY CASCADE'
              INTO statement
              FROM pg_tables
              WHERE schemaname = 'public' AND tablename <> '__EFMigrationsHistory';

              IF statement IS NOT NULL THEN EXECUTE statement; END IF;
            END $$;
            """;

        await command.ExecuteNonQueryAsync();
    }

    private async Task<string?> ResolveAdminConnectionStringAsync()
    {
        if (Environment.GetEnvironmentVariable(EnvironmentVariable) is { Length: > 0 } configured)
        {
            if (await CanConnectAsync(configured))
            {
                return configured;
            }

            SkipReason = $"{EnvironmentVariable} is set but no connection could be opened.";
            return null;
        }

        if (await TryStartContainerAsync() is { } fromContainer)
        {
            return fromContainer;
        }

        foreach (var candidate in LocalCandidates())
        {
            if (await CanConnectAsync(candidate))
            {
                return candidate;
            }
        }

        return null;
    }

    private async Task<string?> TryStartContainerAsync()
    {
        try
        {
            _container = new PostgreSqlBuilder()
                .WithImage("postgres:16-alpine")
                .WithDatabase("griffgym")
                .WithUsername("griffgym")
                .WithPassword("griffgym")
                .Build();

            await _container.StartAsync();

            return _container.GetConnectionString();
        }
        catch (Exception)
        {
            // No Docker daemon, no image, no network. All of them mean the same thing here:
            // fall through and look for a server that is already running.
            if (_container is not null)
            {
                await _container.DisposeAsync();
                _container = null;
            }

            return null;
        }
    }

    /// <summary>
    /// A Unix socket first: it is what a local Homebrew or apt PostgreSQL listens on, and it
    /// works in sandboxes where loopback TCP does not.
    /// </summary>
    private static IEnumerable<string> LocalCandidates()
    {
        var user = Environment.UserName;

        foreach (var directory in new[] { "/tmp", "/var/run/postgresql" })
        {
            if (Directory.Exists(directory) && File.Exists(Path.Combine(directory, ".s.PGSQL.5432")))
            {
                yield return $"Host={directory};Port=5432;Database=postgres;Username=griffgym;Password=griffgym;Timeout=5";
                yield return $"Host={directory};Port=5432;Database=postgres;Username={user};Timeout=5";
            }
        }

        yield return "Host=localhost;Port=5432;Database=postgres;Username=griffgym;Password=griffgym;Timeout=5";
        yield return "Host=localhost;Port=5432;Database=postgres;Username=postgres;Password=postgres;Timeout=5";
    }

    private static async Task<bool> CanConnectAsync(string connectionString)
    {
        try
        {
            await using var connection = new NpgsqlConnection(connectionString);
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(8));

            await connection.OpenAsync(timeout.Token);

            return true;
        }
        catch (Exception)
        {
            return false;
        }
    }

    private async Task ExecuteOnAdminAsync(string sql)
    {
        await using var connection = new NpgsqlConnection(_adminConnectionString);
        await connection.OpenAsync();

        await using var command = connection.CreateCommand();
        command.CommandText = sql;

        await command.ExecuteNonQueryAsync();
    }

    public async ValueTask DisposeAsync()
    {
        GC.SuppressFinalize(this);

        if (_container is not null)
        {
            await _container.DisposeAsync();
            return;
        }

        if (_databaseName is null || _adminConnectionString is null)
        {
            return;
        }

        try
        {
            // Npgsql pools connections, and PostgreSQL will not drop a database anybody is still
            // attached to.
            NpgsqlConnection.ClearAllPools();
            await ExecuteOnAdminAsync($"DROP DATABASE IF EXISTS \"{_databaseName}\" WITH (FORCE)");
        }
        catch (Exception)
        {
            // A leftover test database is untidy, not a test failure.
        }
    }
}
