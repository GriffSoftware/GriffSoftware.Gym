using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Abstractions.Security;
using GriffGym.Application.Common;
using GriffGym.Domain.Users;
using Microsoft.Extensions.Logging;

namespace GriffGym.Application.Auth;

public sealed class LoginUserUseCase(
    IUserRepository users,
    IUnitOfWork unitOfWork,
    IPasswordHasher passwordHasher,
    IAuthenticationSessionService sessions,
    IClock clock,
    ILogger<LoginUserUseCase> logger)
{
    /// <summary>
    /// A hash of a value nobody knows, verified against when the account does not exist.
    ///
    /// Without it, "no such account" returns in microseconds while "wrong password" spends
    /// PBKDF2's full work factor, and the difference is a free account-enumeration oracle.
    /// </summary>
    private readonly Lazy<string> _decoyHash =
        new(() => passwordHasher.Hash(Guid.NewGuid().ToString("N")));

    public async Task<AuthenticationResult> ExecuteAsync(
        LoginUserCommand command,
        CancellationToken cancellationToken)
    {
        var normalized = EmailAddress.Normalize(command.Email);
        var user = await users.FindByNormalizedEmailAsync(normalized, cancellationToken);

        if (user is null)
        {
            passwordHasher.Verify(_decoyHash.Value, command.Password);
            logger.LogInformation("Login failed: no account for the supplied address");
            throw new AuthenticationFailedException("Invalid email address or password.");
        }

        var outcome = passwordHasher.Verify(user.PasswordHash, command.Password);
        if (outcome == PasswordVerificationOutcome.Failed)
        {
            logger.LogInformation("Login failed for {UserId}", user.Id);
            throw new AuthenticationFailedException("Invalid email address or password.");
        }

        if (outcome == PasswordVerificationOutcome.SuccessRehashNeeded)
        {
            // The password was right; the stored hash is simply older than the current work
            // factor. Upgrading it here is how a parameter bump reaches accounts that never
            // change their password.
            user.UpgradePasswordHash(passwordHasher.Hash(command.Password), clock.UtcNow);
        }

        var result = await sessions.IssueAsync(user, command.DeviceId, cancellationToken);
        await unitOfWork.SaveChangesAsync(cancellationToken);

        logger.LogInformation("User logged in {UserId}", user.Id);

        return result;
    }
}
