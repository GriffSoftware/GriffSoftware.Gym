namespace GriffGym.Application.Auth;

/// <summary>
/// The parts of the authentication configuration the application layer reasons about.
///
/// Signing keys, issuer and audience are not here: those are infrastructure's business, and
/// the use cases must not be able to touch them even by accident.
/// </summary>
public sealed class AuthenticationSettings
{
    /// <summary>
    /// Long enough that a lifter is not refreshing between sets, short enough that a leaked
    /// access token stops working before it is useful.
    /// </summary>
    public TimeSpan AccessTokenLifetime { get; set; } = TimeSpan.FromMinutes(15);

    /// <summary>
    /// A month of not opening the app should not log anybody out. The token rotates on every
    /// use, so a long life is not a long-lived secret.
    /// </summary>
    public TimeSpan RefreshTokenLifetime { get; set; } = TimeSpan.FromDays(30);
}
