namespace GriffGym.Api.Contracts.V1;

/// <summary>
/// <c>DeviceId</c> is an opaque label the app picks for the installation. It exists so a lifter
/// can hold several live sessions at once — phone, old phone, tablet — and it is never treated
/// as a credential or as proof of anything.
/// </summary>
public sealed record RegisterRequest(string Email, string Password, string? DeviceId);

public sealed record LoginRequest(string Email, string Password, string? DeviceId);

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
