namespace GriffGym.Application.Auth;

public sealed record RegisterUserCommand(string Email, string Password, string? DeviceId);

public sealed record LoginUserCommand(string Email, string Password, string? DeviceId);

public sealed record RefreshTokenCommand(string RefreshToken, string? DeviceId);

public sealed record LogoutCommand(string RefreshToken);

/// <summary>
/// What a client gets back from register, login and refresh.
///
/// The refresh token appears here exactly once, in the response that mints it. It is never
/// readable again — only its hash is stored.
/// </summary>
public sealed record AuthenticationResult(
    Guid UserId,
    string Email,
    string AccessToken,
    DateTimeOffset AccessTokenExpiresAtUtc,
    int AccessTokenExpiresInSeconds,
    string RefreshToken,
    DateTimeOffset RefreshTokenExpiresAtUtc);
