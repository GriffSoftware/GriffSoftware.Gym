using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Abstractions.Security;
using GriffGym.Domain.Users;
using Microsoft.Extensions.Logging;

namespace GriffGym.Application.Auth;

/// <summary>
/// Ends one device's session by revoking the refresh token it holds.
///
/// Succeeds whether or not the token was real. Logout is not an authorisation decision, and
/// answering "that token does not exist" would let anyone probe for live tokens.
/// </summary>
public sealed class LogoutUserUseCase(
    IRefreshTokenRepository refreshTokens,
    IUnitOfWork unitOfWork,
    IRefreshTokenGenerator refreshTokenGenerator,
    IClock clock,
    ILogger<LogoutUserUseCase> logger)
{
    public async Task ExecuteAsync(LogoutCommand command, CancellationToken cancellationToken)
    {
        var hash = refreshTokenGenerator.HashPresented(command.RefreshToken);
        var stored = await refreshTokens.FindByHashAsync(hash, cancellationToken);

        if (stored is null)
        {
            return;
        }

        stored.Revoke(clock.UtcNow, RefreshTokenRevocationReason.LoggedOut);
        await unitOfWork.SaveChangesAsync(cancellationToken);

        logger.LogInformation("Session ended for {UserId}", stored.UserId);
    }
}

/// <summary>Signs the lifter out everywhere — the "I lost my phone" button.</summary>
public sealed class LogoutAllSessionsUseCase(
    IRefreshTokenRepository refreshTokens,
    IUnitOfWork unitOfWork,
    ICurrentUser currentUser,
    IClock clock,
    ILogger<LogoutAllSessionsUseCase> logger)
{
    public async Task ExecuteAsync(CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();
        var now = clock.UtcNow;

        var active = await refreshTokens.ListActiveForUserAsync(userId, now, cancellationToken);
        foreach (var token in active)
        {
            token.Revoke(now, RefreshTokenRevocationReason.LoggedOutEverywhere);
        }

        await unitOfWork.SaveChangesAsync(cancellationToken);

        logger.LogInformation("All {Count} sessions ended for {UserId}", active.Count, userId);
    }
}
