using GriffGym.Domain.Users;

namespace GriffGym.Application.Auth;

/// <summary>
/// Mints an access token plus a fresh refresh token for one device.
///
/// Register, login and refresh all end the same way, and duplicating "make two tokens, hash
/// one, store it, return the other" three times is how the three drift apart.
/// </summary>
public interface IAuthenticationSessionService
{
    Task<AuthenticationResult> IssueAsync(
        User user,
        string? deviceId,
        CancellationToken cancellationToken);

    /// <summary>Issues a session that replaces <paramref name="rotated"/>, retiring it.</summary>
    Task<AuthenticationResult> RotateAsync(
        User user,
        RefreshToken rotated,
        string? deviceId,
        CancellationToken cancellationToken);
}
