using System.Text.Json;
using System.Text.Json.Serialization;
using GriffGym.TestSupport;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace GriffGym.Api.IntegrationTests;

/// <summary>
/// The real application, hosted in-process against a real PostgreSQL.
///
/// Nothing is stubbed out: the same controllers, the same validation filter, the same JWT
/// middleware, the same EF Core mappings. A test that swapped the database for a fake would
/// pass while the migration was wrong, and one that bypassed authentication would pass while
/// every endpoint was open to the world.
/// </summary>
public sealed class GriffGymApiFactory(string connectionString) : WebApplicationFactory<Program>
{
    /// <summary>
    /// Long enough to be a real HMAC key, and obviously worthless outside the test run.
    /// </summary>
    private const string TestSigningKey = "integration-test-signing-key-please-do-not-ship-this";

    public static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web)
    {
        Converters = { new JsonStringEnumConverter() },
    };

    /// <summary>Raised for the suite; a dedicated test lowers it to check the limiter works.</summary>
    public int AuthenticationPermitsPerMinute { get; init; } = 10_000;

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        // UseSetting, not ConfigureAppConfiguration.
        //
        // Program.cs reads its connection string and JWT section straight off
        // builder.Configuration while it is still being built. Configuration sources added
        // through ConfigureAppConfiguration are not merged until builder.Build(), by which point
        // that code has already run and seen nothing. Host settings are there from the start.
        builder.UseSetting(WebHostDefaults.EnvironmentKey, Environments.Staging);

        builder.UseSetting("ConnectionStrings:GriffGym", connectionString);

        builder.UseSetting("Jwt:Issuer", "griffgym-api-tests");
        builder.UseSetting("Jwt:Audience", "griffgym-app-tests");
        builder.UseSetting("Jwt:SigningKey", TestSigningKey);
        builder.UseSetting("Jwt:AccessTokenMinutes", "15");
        builder.UseSetting("Jwt:RefreshTokenDays", "30");
        builder.UseSetting("Jwt:ClockSkewSeconds", "0");

        // The fixture migrates the database; the application must not race it.
        builder.UseSetting("GriffGym:ApplyMigrationsOnStartup", "false");

        builder.UseSetting(
            "RateLimiting:AuthenticationPermitsPerMinute",
            AuthenticationPermitsPerMinute.ToString(System.Globalization.CultureInfo.InvariantCulture));
        builder.UseSetting("RateLimiting:GeneralPermitsPerMinute", "100000");

        builder.ConfigureLogging(logging => logging.SetMinimumLevel(LogLevel.Warning));
    }
}
