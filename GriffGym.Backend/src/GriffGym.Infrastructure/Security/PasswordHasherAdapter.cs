using GriffGym.Application.Abstractions.Security;
using GriffGym.Domain.Users;
using Microsoft.AspNetCore.Identity;

namespace GriffGym.Infrastructure.Security;

/// <summary>
/// Password hashing, delegated wholesale to ASP.NET Core Identity's implementation.
///
/// Nothing here invents a scheme. <see cref="PasswordHasher{TUser}"/> in its V3 format is
/// PBKDF2-HMAC-SHA512 with a 128-bit random salt and a large iteration count, and it compares in
/// fixed time. It also reports when a stored hash was produced with older parameters, which is
/// what lets a work-factor increase reach accounts whose owners never change their password.
/// </summary>
internal sealed class PasswordHasherAdapter : IPasswordHasher
{
    private readonly PasswordHasher<User> _hasher = new();

    /// <summary>
    /// The hasher's API takes a user so that a future format could bind a hash to an account.
    /// V3 does not, so a placeholder is honest and avoids loading a user just to verify one.
    /// </summary>
    private static readonly User Placeholder = User.Register(
        Guid.Parse("00000000-0000-0000-0000-000000000001"),
        EmailAddress.Of("placeholder@griffgym.invalid"),
        "-",
        DateTimeOffset.UnixEpoch);

    public string Hash(string password) => _hasher.HashPassword(Placeholder, password);

    public PasswordVerificationOutcome Verify(string passwordHash, string providedPassword) =>
        _hasher.VerifyHashedPassword(Placeholder, passwordHash, providedPassword) switch
        {
            PasswordVerificationResult.Success => PasswordVerificationOutcome.Success,
            PasswordVerificationResult.SuccessRehashNeeded =>
                PasswordVerificationOutcome.SuccessRehashNeeded,
            _ => PasswordVerificationOutcome.Failed,
        };
}
