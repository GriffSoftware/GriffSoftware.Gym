using System.ComponentModel.DataAnnotations;

namespace GriffGym.Infrastructure.Security;

/// <summary>
/// Token signing configuration.
///
/// <see cref="SigningKey"/> has no default and never appears in a checked-in appsettings file.
/// In production it arrives as an environment variable; in development, user secrets or a local
/// override. Validation is on startup, so a misconfigured deployment fails immediately instead
/// of issuing tokens signed with something predictable.
/// </summary>
public sealed class JwtOptions
{
    public const string SectionName = "Jwt";

    /// <summary>256 bits. A short signing key is a forged token waiting to happen.</summary>
    public const int MinimumSigningKeyBytes = 32;

    [Required(AllowEmptyStrings = false)]
    public string Issuer { get; init; } = string.Empty;

    [Required(AllowEmptyStrings = false)]
    public string Audience { get; init; } = string.Empty;

    [Required(AllowEmptyStrings = false)]
    public string SigningKey { get; init; } = string.Empty;

    [Range(1, 120)]
    public int AccessTokenMinutes { get; init; } = 15;

    [Range(1, 365)]
    public int RefreshTokenDays { get; init; } = 30;

    /// <summary>
    /// No leeway by default. The five minutes ASP.NET Core allows out of the box quietly extends
    /// every access token's life by a third.
    /// </summary>
    [Range(0, 300)]
    public int ClockSkewSeconds { get; init; }
}
