using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Abstractions.Security;
using GriffGym.Domain.Users;
using Microsoft.Extensions.Options;

namespace GriffGym.Application.Auth;

public sealed class AuthenticationSessionService(
    IRefreshTokenRepository refreshTokens,
    IAccessTokenIssuer accessTokens,
    IRefreshTokenGenerator refreshTokenGenerator,
    IIdentifierFactory identifiers,
    IClock clock,
    IOptions<AuthenticationSettings> settings) : IAuthenticationSessionService
{
    private readonly AuthenticationSettings _settings = settings.Value;

    public Task<AuthenticationResult> IssueAsync(
        User user,
        string? deviceId,
        CancellationToken cancellationToken) =>
        Task.FromResult(Issue(user, deviceId, rotated: null));

    public Task<AuthenticationResult> RotateAsync(
        User user,
        RefreshToken rotated,
        string? deviceId,
        CancellationToken cancellationToken) =>
        Task.FromResult(Issue(user, deviceId ?? rotated.DeviceId, rotated));

    private AuthenticationResult Issue(User user, string? deviceId, RefreshToken? rotated)
    {
        var now = clock.UtcNow;
        var accessToken = accessTokens.Issue(user);
        var material = refreshTokenGenerator.Generate();

        var refreshToken = RefreshToken.Issue(
            identifiers.NewId(),
            user.Id,
            material.Hash,
            deviceId,
            now,
            _settings.RefreshTokenLifetime);

        refreshTokens.Add(refreshToken);
        rotated?.RotateTo(refreshToken, now);

        return new AuthenticationResult(
            user.Id,
            user.Email.Value,
            accessToken.Value,
            accessToken.ExpiresAtUtc,
            (int)accessToken.Lifetime.TotalSeconds,
            material.Value,
            refreshToken.ExpiresAtUtc);
    }
}
