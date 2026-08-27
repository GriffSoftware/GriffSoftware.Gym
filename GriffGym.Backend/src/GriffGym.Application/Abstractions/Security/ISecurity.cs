using GriffGym.Domain.Users;

namespace GriffGym.Application.Abstractions.Security;

public enum PasswordVerificationOutcome
{
    Failed = 0,
    Success = 1,

    /// <summary>
    /// The password is right but the stored hash uses outdated parameters. The application
    /// re-hashes on the next successful login, so a work-factor bump reaches existing accounts
    /// without anybody having to reset anything.
    /// </summary>
    SuccessRehashNeeded = 2,
}

/// <summary>
/// Password hashing, delegated to a vetted Microsoft implementation.
///
/// Nothing here invents a hashing scheme; the infrastructure implementation wraps
/// <c>Microsoft.AspNetCore.Identity.PasswordHasher&lt;T&gt;</c>, which is PBKDF2-HMAC-SHA512
/// with a per-password salt and a large iteration count, and verifies in constant time.
/// </summary>
public interface IPasswordHasher
{
    string Hash(string password);

    PasswordVerificationOutcome Verify(string passwordHash, string providedPassword);
}

/// <summary>A signed JWT and the moment it stops being accepted.</summary>
public sealed record AccessToken(string Value, DateTimeOffset ExpiresAtUtc, TimeSpan Lifetime);

public interface IAccessTokenIssuer
{
    AccessToken Issue(User user);
}

/// <summary>
/// A refresh token as the client sees it, paired with the hash the server keeps.
///
/// Only <see cref="Hash"/> is ever written to the database. A stolen backup must not hand over
/// working credentials, for the same reason passwords are not stored in the clear.
/// </summary>
public sealed record RefreshTokenMaterial(string Value, string Hash);

public interface IRefreshTokenGenerator
{
    RefreshTokenMaterial Generate();

    /// <summary>Hashes a token a client presented, so it can be looked up by hash.</summary>
    string HashPresented(string token);
}
