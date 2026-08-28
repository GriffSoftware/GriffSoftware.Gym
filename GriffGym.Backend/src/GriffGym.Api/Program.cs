using System.Text;
using System.Text.Json.Serialization;
using FluentValidation;
using GriffGym.Api.Controllers.V1;
using GriffGym.Api.Errors;
using GriffGym.Api.OpenApi;
using GriffGym.Api.Security;
using GriffGym.Api.Validation;
using GriffGym.Application;
using GriffGym.Application.Abstractions;
using GriffGym.Infrastructure;
using GriffGym.Infrastructure.Persistence;
using GriffGym.Infrastructure.Security;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Diagnostics.HealthChecks;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Diagnostics.HealthChecks;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);

// ------------------------------------------------------------------------------------------
// Composition root. This is the only file that knows all four layers exist at once.
// ------------------------------------------------------------------------------------------

builder.Services.AddGriffGymApplication();
builder.Services.AddGriffGymInfrastructure(builder.Configuration);

builder.Services.AddHttpContextAccessor();
builder.Services.AddScoped<ICurrentUser, CurrentUser>();

builder.Services
    .AddControllers(options => options.Filters.Add<ValidationActionFilter>())
    .AddJsonOptions(options =>
    {
        // Enums as names, not ordinals. "TOP" survives somebody inserting a value into the enum
        // next year; 1 does not, and a mobile client that has not been updated would silently
        // start reading the wrong exercise type.
        options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter());
        options.JsonSerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
    });

builder.Services.AddValidatorsFromAssemblyContaining<RegisterRequestValidator>();

// ProblemDetails for everything, including the framework's own 401s and 404s, so a client never
// has to parse two different error shapes.
builder.Services.AddProblemDetails();
builder.Services.AddExceptionHandler<GlobalExceptionHandler>();

var jwt = builder.Configuration.GetSection(JwtOptions.SectionName).Get<JwtOptions>()
          ?? throw new InvalidOperationException("The 'Jwt' configuration section is missing.");

builder.Services
    .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.MapInboundClaims = false;
        options.TokenValidationParameters = new TokenValidationParameters
        {
            // Every one of these on purpose. A token is only accepted if it was signed by this
            // deployment's key, minted for this API, and has not expired.
            ValidateIssuer = true,
            ValidIssuer = jwt.Issuer,
            ValidateAudience = true,
            ValidAudience = jwt.Audience,
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwt.SigningKey)),
            ValidateLifetime = true,
            // The framework's default five minutes of leeway quietly extends a fifteen-minute
            // access token by a third.
            ClockSkew = TimeSpan.FromSeconds(jwt.ClockSkewSeconds),
            NameClaimType = "sub",
        };

        // Signature and expiry only prove the token was genuine when it was minted. This asks
        // the further question the account-deletion feature depends on: does the account it
        // names still exist, and is this token still the current one for it?
        options.Events = new JwtBearerEvents
        {
            OnTokenValidated = AccessTokenValidation.EnsureAccountIsStillActiveAsync,
        };
    });

builder.Services.AddAuthorization();

builder.Services.AddOptions<RateLimitOptions>()
    .Bind(builder.Configuration.GetSection(RateLimitOptions.SectionName))
    .ValidateDataAnnotations()
    .ValidateOnStart();

builder.Services.AddRateLimiter(RateLimitPolicies.Configure);

builder.Services.AddOpenApi(options =>
    options.AddDocumentTransformer<BearerSecuritySchemeTransformer>());

builder.Services
    .AddHealthChecks()
    .AddNpgSql(
        builder.Configuration.GetConnectionString(
            GriffGym.Infrastructure.DependencyInjection.ConnectionStringName)
        ?? string.Empty,
        name: "postgres",
        failureStatus: HealthStatus.Unhealthy,
        tags: ["ready"]);

var app = builder.Build();

app.UseExceptionHandler();

// No CORS. The only client is a native Android app, which is not a browser and is not subject
// to the same-origin policy — configuring CORS here would loosen the API for no one's benefit.
// A future web client is the moment to add it, with its own origin named explicitly.

app.UseRateLimiter();
app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
    app.UseSwaggerUI(options =>
    {
        options.SwaggerEndpoint("/openapi/v1.json", "Griff Gym API v1");
        options.DocumentTitle = "Griff Gym API";
    });
}

// Liveness answers "is the process up?" and must not touch the database: a dead database is a
// reason to stop routing traffic here, not a reason for an orchestrator to kill and restart the
// container in a loop. Readiness is the one that checks PostgreSQL.
app.MapHealthChecks("/health/live", new HealthCheckOptions { Predicate = _ => false })
    .AllowAnonymous();

app.MapHealthChecks("/health/ready", new HealthCheckOptions
{
    Predicate = registration => registration.Tags.Contains("ready"),
}).AllowAnonymous();

app.MapHealthChecks("/health").AllowAnonymous();

await ApplyMigrationsIfRequestedAsync(app);

await app.RunAsync();

/// <summary>
/// Migrations run on startup only when explicitly asked for, and never by default.
///
/// Production applies them as a deliberate deployment step (<c>dotnet ef database update</c>, or
/// a one-shot container) — see README.md. Automatic migration on boot means every replica races
/// to alter the schema at once, and a bad migration takes the service down with no way to stop
/// it. Development turns it on for convenience via <c>GriffGym:ApplyMigrationsOnStartup</c>.
/// </summary>
static async Task ApplyMigrationsIfRequestedAsync(WebApplication app)
{
    if (!app.Configuration.GetValue("GriffGym:ApplyMigrationsOnStartup", false))
    {
        return;
    }

    await using var scope = app.Services.CreateAsyncScope();
    var context = scope.ServiceProvider.GetRequiredService<GriffGymDbContext>();

    app.Logger.LogInformation("Applying database migrations on startup");
    await context.Database.MigrateAsync();
}

/// <summary>Exposed so the integration tests can host the real application.</summary>
public partial class Program;
