using GriffGym.Domain.Common;

namespace GriffGym.Domain.Users;

/// <summary>
/// An account. Everything else in this model hangs off one of these by <see cref="Entity.Id"/>.
///
/// The password is present only as a hash produced by a vetted Microsoft component, and the
/// domain never sees a plaintext password: hashing is a capability the application layer asks
/// infrastructure for.
/// </summary>
public sealed class User : Entity
{
    private User(
        Guid id,
        EmailAddress email,
        string passwordHash,
        string securityStamp,
        DateTimeOffset createdAtUtc,
        DateTimeOffset updatedAtUtc)
        : base(id, createdAtUtc, updatedAtUtc)
    {
        DomainException.Require(
            !string.IsNullOrWhiteSpace(passwordHash),
            "A user must have a password hash.");

        Email = email;
        PasswordHash = passwordHash;
        SecurityStamp = securityStamp;
    }

    public EmailAddress Email { get; private set; }

    public string PasswordHash { get; private set; }

    /// <summary>
    /// Changes whenever the credentials change. Access tokens carry it as a claim, so a future
    /// password change can invalidate tokens that were already issued without waiting for them
    /// to expire.
    /// </summary>
    public string SecurityStamp { get; private set; }

    public static User Register(
        Guid id,
        EmailAddress email,
        string passwordHash,
        DateTimeOffset now) =>
        new(id, email, passwordHash, NewSecurityStamp(), now, now);

    public static User FromStorage(
        Guid id,
        EmailAddress email,
        string passwordHash,
        string securityStamp,
        DateTimeOffset createdAtUtc,
        DateTimeOffset updatedAtUtc) =>
        new(id, email, passwordHash, securityStamp, createdAtUtc, updatedAtUtc);

    public void ChangePassword(string passwordHash, DateTimeOffset now)
    {
        DomainException.Require(
            !string.IsNullOrWhiteSpace(passwordHash),
            "A user must have a password hash.");

        PasswordHash = passwordHash;
        SecurityStamp = NewSecurityStamp();
        Touch(now);
    }

    /// <summary>Re-hashing after a hasher upgrade is not a credential change: the stamp stands.</summary>
    public void UpgradePasswordHash(string passwordHash, DateTimeOffset now)
    {
        DomainException.Require(
            !string.IsNullOrWhiteSpace(passwordHash),
            "A user must have a password hash.");

        PasswordHash = passwordHash;
        Touch(now);
    }

    private static string NewSecurityStamp() => Guid.NewGuid().ToString("N");
}
