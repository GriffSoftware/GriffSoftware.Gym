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
        string? googleSubjectId,
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
        GoogleSubjectId = googleSubjectId;
    }

    public EmailAddress Email { get; private set; }

    public string PasswordHash { get; private set; }

    /// <summary>
    /// Changes whenever the credentials change. Access tokens carry it as a claim, so a future
    /// password change can invalidate tokens that were already issued without waiting for them
    /// to expire.
    /// </summary>
    public string SecurityStamp { get; private set; }

    /// <summary>
    /// Google's stable per-account identifier ("sub" claim), once this account has signed in
    /// with Google at least once. Null for an account that has only ever used a password.
    ///
    /// <see cref="PasswordHash"/> stays required even here: a Google-only account gets a random,
    /// unusable hash rather than reshaping every use case around an optional password.
    /// </summary>
    public string? GoogleSubjectId { get; private set; }

    public static User Register(
        Guid id,
        EmailAddress email,
        string passwordHash,
        DateTimeOffset now) =>
        new(id, email, passwordHash, NewSecurityStamp(), googleSubjectId: null, now, now);

    public static User FromStorage(
        Guid id,
        EmailAddress email,
        string passwordHash,
        string securityStamp,
        string? googleSubjectId,
        DateTimeOffset createdAtUtc,
        DateTimeOffset updatedAtUtc) =>
        new(id, email, passwordHash, securityStamp, googleSubjectId, createdAtUtc, updatedAtUtc);

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

    /// <summary>
    /// Records that this account can now also sign in with this Google identity. Idempotent by
    /// design — signing in again with the same Google account calls this every time.
    /// </summary>
    public void LinkGoogleAccount(string googleSubjectId, DateTimeOffset now)
    {
        DomainException.Require(
            !string.IsNullOrWhiteSpace(googleSubjectId),
            "A Google subject id must not be blank.");

        if (GoogleSubjectId == googleSubjectId)
        {
            return;
        }

        GoogleSubjectId = googleSubjectId;
        Touch(now);
    }

    private static string NewSecurityStamp() => Guid.NewGuid().ToString("N");
}
