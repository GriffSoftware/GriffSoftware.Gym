using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Abstractions.Security;
using GriffGym.Application.Common;
using GriffGym.Domain.Users;
using Microsoft.Extensions.Logging;

namespace GriffGym.Application.Auth;

/// <summary>
/// Exchanges a refresh token for a new pair, retiring the one that was presented.
///
/// Rotation is the point: a token is good for exactly one use. That turns a stolen token into
/// a race the thief has to win, and — because the loser then presents an already-rotated
/// token — makes the theft detectable rather than silent.
/// </summary>
public sealed class RefreshTokenUseCase(
    IUserRepository users,
    IRefreshTokenRepository refreshTokens,
    IUnitOfWork unitOfWork,
    IRefreshTokenGenerator refreshTokenGenerator,
    IAuthenticationSessionService sessions,
    IClock clock,
    ILogger<RefreshTokenUseCase> logger)
{
    public async Task<AuthenticationResult> ExecuteAsync(
        RefreshTokenCommand command,
        CancellationToken cancellationToken)
    {
        var hash = refreshTokenGenerator.HashPresented(command.RefreshToken);
        var stored = await refreshTokens.FindByHashAsync(hash, cancellationToken);

        if (stored is null)
        {
            logger.LogInformation("Refresh rejected: unknown token");
            throw new AuthenticationFailedException("The refresh token is not valid.");
        }

        var now = clock.UtcNow;

        if (stored.WasRotated)
        {
            // Somebody is holding a token that was already exchanged. Either it leaked or a
            // client is badly broken; both mean every token in this family has to go, and the
            // lifter signs in again on every device.
            await RevokeEverythingAsync(stored.UserId, now, cancellationToken);
            await unitOfWork.SaveChangesAsync(cancellationToken);

            logger.LogWarning(
                "Refresh token reuse detected for {UserId}; all sessions revoked",
                stored.UserId);

            throw new AuthenticationFailedException("The refresh token is not valid.");
        }

        if (!stored.IsActiveAt(now))
        {
            logger.LogInformation(
                "Refresh rejected for {UserId}: token expired or revoked",
                stored.UserId);
            throw new AuthenticationFailedException("The refresh token is not valid.");
        }

        var user = await users.FindByIdAsync(stored.UserId, cancellationToken)
                   ?? throw new AuthenticationFailedException("The refresh token is not valid.");

        var result = await sessions.RotateAsync(user, stored, command.DeviceId, cancellationToken);
        await unitOfWork.SaveChangesAsync(cancellationToken);

        logger.LogInformation("Refresh token rotated for {UserId}", user.Id);

        return result;
    }

    private async Task RevokeEverythingAsync(
        Guid userId,
        DateTimeOffset now,
        CancellationToken cancellationToken)
    {
        var active = await refreshTokens.ListActiveForUserAsync(userId, now, cancellationToken);
        foreach (var token in active)
        {
            token.Revoke(now, RefreshTokenRevocationReason.ReuseDetected);
        }
    }
}
