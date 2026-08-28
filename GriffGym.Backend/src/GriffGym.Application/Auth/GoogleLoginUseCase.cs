using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Abstractions.Security;
using GriffGym.Application.Common;
using GriffGym.Domain.Users;
using Microsoft.Extensions.Logging;

namespace GriffGym.Application.Auth;

/// <summary>
/// Signs in with a Google ID token, registering an account the first time one is seen.
///
/// A returning Google subject is matched directly. A first-time one is matched by email instead
/// — Google has already verified that address, so folding it into an existing password account
/// is exactly as safe as folding in a new one, and a lifter who registered with a password never
/// ends up with two accounts just because they later tapped "Sign in with Google".
/// </summary>
public sealed class GoogleLoginUseCase(
    IUserRepository users,
    IUnitOfWork unitOfWork,
    IGoogleIdTokenValidator googleTokens,
    IPasswordHasher passwordHasher,
    IAuthenticationSessionService sessions,
    IIdentifierFactory identifiers,
    IClock clock,
    ILogger<GoogleLoginUseCase> logger)
{
    public async Task<AuthenticationResult> ExecuteAsync(
        GoogleLoginCommand command,
        CancellationToken cancellationToken)
    {
        var identity = await googleTokens.ValidateAsync(command.IdToken, cancellationToken);

        if (!identity.EmailVerified)
        {
            // Google issues this for a small number of legacy/unverified accounts. Nothing here
            // can trust the address in that case, so there is no safe way to sign this in.
            throw new AuthenticationFailedException("This Google account's email is not verified.");
        }

        var user = await users.FindByGoogleSubjectIdAsync(identity.Subject, cancellationToken);
        var now = clock.UtcNow;

        if (user is null)
        {
            var normalized = EmailAddress.Normalize(identity.Email);
            user = await users.FindByNormalizedEmailAsync(normalized, cancellationToken);

            if (user is null)
            {
                // Signs in with Google only — the domain still requires a password hash, so a
                // random one nobody knows fills the slot rather than making it optional.
                var unusablePasswordHash = passwordHasher.Hash(Guid.NewGuid().ToString("N"));
                user = User.Register(
                    identifiers.NewId(),
                    EmailAddress.Of(identity.Email),
                    unusablePasswordHash,
                    now);

                users.Add(user);
                logger.LogInformation("User registered via Google {UserId}", user.Id);
            }

            user.LinkGoogleAccount(identity.Subject, now);
        }

        var result = await sessions.IssueAsync(user, command.DeviceId, cancellationToken);
        await unitOfWork.SaveChangesAsync(cancellationToken);

        logger.LogInformation("User logged in via Google {UserId}", user.Id);

        return result;
    }
}
