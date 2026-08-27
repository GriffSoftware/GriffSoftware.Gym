using System.ComponentModel.DataAnnotations;
using System.Globalization;
using System.Threading.RateLimiting;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.Extensions.Options;

namespace GriffGym.Api.Controllers.V1;

/// <summary>
/// How many requests a minute each bucket allows.
///
/// Configurable rather than hard-coded so a deployment behind a shared NAT can raise the
/// general limit without a rebuild — and so the limiter itself can be tested at values that do
/// not make the suite take a minute per assertion.
/// </summary>
public sealed class RateLimitOptions
{
    public const string SectionName = "RateLimiting";

    /// <summary>
    /// Credential endpoints. Ten a minute is far more than a lifter signing in needs, and far
    /// less than a password-guessing script wants.
    /// </summary>
    [Range(1, 100000)]
    public int AuthenticationPermitsPerMinute { get; init; } = 10;

    /// <summary>
    /// Everything else. A lifter logging a set every ninety seconds comes nowhere near this;
    /// it is here so one broken client cannot saturate the process.
    /// </summary>
    [Range(1, 1000000)]
    public int GeneralPermitsPerMinute { get; init; } = 300;
}

public static class RateLimitPolicies
{
    /// <summary>The named policy on the credential endpoints. Everything else gets the global limiter.</summary>
    public const string Authentication = "auth";

    public static void Configure(RateLimiterOptions options)
    {
        options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;

        // The general bucket is the *global* limiter, not a named policy applied to every
        // endpoint. That distinction matters: an endpoint carries one rate-limiting policy, so
        // attaching "general" to all controllers would replace the tighter "auth" policy on the
        // credential endpoints and quietly leave login unthrottled. The global limiter runs in
        // addition to whatever policy an endpoint declares, so both apply.
        options.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(
            httpContext => Window(httpContext, Limits(httpContext).GeneralPermitsPerMinute));

        // Limits are read per request from the request's own container rather than captured
        // here. Resolving them at configuration time would mean building a second service
        // provider before the real one exists — a well-known way to end up with two copies of
        // every singleton.
        options.AddPolicy(Authentication, httpContext => Window(
            httpContext,
            Limits(httpContext).AuthenticationPermitsPerMinute));

        options.OnRejected = async (context, cancellationToken) =>
        {
            if (context.Lease.TryGetMetadata(MetadataName.RetryAfter, out var retryAfter))
            {
                context.HttpContext.Response.Headers.RetryAfter =
                    ((int)retryAfter.TotalSeconds).ToString(CultureInfo.InvariantCulture);
            }

            context.HttpContext.Response.ContentType = "application/problem+json";

            await context.HttpContext.Response.WriteAsync(
                """
                {"type":"https://httpstatuses.io/429","title":"Too many requests","status":429,"detail":"Slow down and try again shortly."}
                """,
                cancellationToken);
        };
    }

    private static RateLimitOptions Limits(HttpContext httpContext) =>
        httpContext.RequestServices.GetRequiredService<IOptions<RateLimitOptions>>().Value;

    private static RateLimitPartition<string> Window(HttpContext httpContext, int permitLimit) =>
        RateLimitPartition.GetFixedWindowLimiter(
            PartitionKey(httpContext),
            _ => new FixedWindowRateLimiterOptions
            {
                PermitLimit = permitLimit,
                Window = TimeSpan.FromMinutes(1),
                // No queue. Making a failed login wait achieves nothing; refusing it outright is
                // the signal a client should back off on.
                QueueLimit = 0,
            });

    /// <summary>
    /// Per authenticated lifter where there is one, per address otherwise — so a gym on shared
    /// Wi-Fi is not locked out by one bad client on the same network.
    /// </summary>
    private static string PartitionKey(HttpContext httpContext) =>
        httpContext.User.Identity?.IsAuthenticated == true
            ? $"user:{httpContext.User.FindFirst("sub")?.Value ?? httpContext.User.Identity.Name}"
            : $"ip:{httpContext.Connection.RemoteIpAddress?.ToString() ?? "unknown"}";
}
