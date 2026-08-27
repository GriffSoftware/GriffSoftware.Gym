using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Abstractions.Security;
using GriffGym.Application.Common;
using GriffGym.Domain.Users;
using Microsoft.Extensions.Logging;

namespace GriffGym.Application.Auth;

/// <summary>
/// Creates an account and signs it straight in, so the app never has to make two round trips
/// to get a lifter from "install" to "training".
/// </summary>
public sealed class RegisterUserUseCase(
    IUserRepository users,
    IUnitOfWork unitOfWork,
    IPasswordHasher passwordHasher,
    IAuthenticationSessionService sessions,
    IIdentifierFactory identifiers,
    IClock clock,
    ILogger<RegisterUserUseCase> logger)
{
    public async Task<AuthenticationResult> ExecuteAsync(
        RegisterUserCommand command,
        CancellationToken cancellationToken)
    {
        var email = EmailAddress.Of(command.Email);

        if (await users.EmailExistsAsync(email.Normalized, cancellationToken))
        {
            // Deliberately explicit. Registration is one of the few places where confirming
            // that an address is taken is unavoidable — the alternative is an account the
            // lifter can never sign into — so the rate limiter, not vagueness, is what makes
            // enumeration expensive here.
            throw new ConflictException("An account with this email address already exists.");
        }

        var now = clock.UtcNow;
        var user = User.Register(
            identifiers.NewId(),
            email,
            passwordHasher.Hash(command.Password),
            now);

        users.Add(user);

        var result = await sessions.IssueAsync(user, command.DeviceId, cancellationToken);
        await unitOfWork.SaveChangesAsync(cancellationToken);

        logger.LogInformation("User registered {UserId}", user.Id);

        return result;
    }
}
