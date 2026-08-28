namespace GriffGym.Api.Contracts.V1;

/// <summary>
/// <c>DeviceId</c> is an opaque label the app picks for the installation. It exists so a lifter
/// can hold several live sessions at once — phone, old phone, tablet — and it is never treated
/// as a credential or as proof of anything.
/// </summary>
public sealed record RegisterRequest(string Email, string Password, string? DeviceId);

public sealed record LoginRequest(string Email, string Password, string? DeviceId);

/// <summary>
/// <c>IdToken</c> is the Google ID token the Android app got from Credential Manager after the
/// lifter picked a Google account — never a Google access token, and never anything the app
/// itself asserts about who the user is.
/// </summary>
public sealed record GoogleLoginRequest(string IdToken, string? DeviceId);

public sealed record RefreshRequest(string RefreshToken, string? DeviceId);

public sealed record LogoutRequest(string RefreshToken);

/// <summary>
/// The refresh token appears in exactly one response: the one that mints it. It is never
/// readable again, because only its hash is kept.
/// </summary>
public sealed record AuthenticationResponse(
    Guid UserId,
    string Email,
    string AccessToken,
    string TokenType,
    DateTimeOffset AccessTokenExpiresAtUtc,
    int ExpiresInSeconds,
    string RefreshToken,
    DateTimeOffset RefreshTokenExpiresAtUtc);

public sealed record UserResponse(
    Guid Id,
    string Email,
    DateTimeOffset CreatedAtUtc,
    DateTimeOffset UpdatedAtUtc);
