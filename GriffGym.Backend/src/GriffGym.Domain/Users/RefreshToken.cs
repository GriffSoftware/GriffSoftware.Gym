using GriffGym.Domain.Common;

namespace GriffGym.Domain.Users;

/// <summary>Why a refresh token stopped being usable. Kept for auditing a suspected theft.</summary>
public enum RefreshTokenRevocationReason
{
    None = 0,
    Rotated = 1,
    LoggedOut = 2,
    LoggedOutEverywhere = 3,
    ReuseDetected = 4,
}

/// <summary>
/// One long-lived credential belonging to one device.
///
/// A lifter can hold several at once — phone, old phone, tablet — so sessions are per device,
/// never one per user. Only a hash of the token is stored: a stolen database backup must not
/// hand over working credentials, exactly as it must not hand over passwords.
///
/// Tokens rotate. Using one mints a replacement and revokes the original, and presenting an
/// already-rotated token is treated as theft rather than as a retry, because the legitimate
/// holder would have moved on to the replacement.
/// </summary>
public sealed class RefreshToken : Entity
{
    private RefreshToken(
        Guid id,
        Guid userId,
        string tokenHash,
        string? deviceId,
        DateTimeOffset expiresAtUtc,
        DateTimeOffset? revokedAtUtc,
        RefreshTokenRevocationReason revocationReason,
        Guid? replacedByTokenId,
        DateTimeOffset createdAtUtc,
        DateTimeOffset updatedAtUtc)
        : base(id, createdAtUtc, updatedAtUtc)
    {
        DomainException.Require(userId != Guid.Empty, "A refresh token must belong to a user.");
        DomainException.Require(
            !string.IsNullOrWhiteSpace(tokenHash),
            "A refresh token must be stored as a hash.");
        DomainException.Require(
            expiresAtUtc > createdAtUtc,
            "A refresh token must expire after it was created.");

        UserId = userId;
        TokenHash = tokenHash;
        DeviceId = deviceId;
        ExpiresAtUtc = expiresAtUtc;
        RevokedAtUtc = revokedAtUtc;
        RevocationReason = revocationReason;
        ReplacedByTokenId = replacedByTokenId;
    }

    public Guid UserId { get; }

    public string TokenHash { get; }

    /// <summary>Opaque client-supplied device label, used only to describe a session.</summary>
    public string? DeviceId { get; }

    public DateTimeOffset ExpiresAtUtc { get; }

    public DateTimeOffset? RevokedAtUtc { get; private set; }

    public RefreshTokenRevocationReason RevocationReason { get; private set; }

    public Guid? ReplacedByTokenId { get; private set; }

    public bool IsRevoked => RevokedAtUtc is not null;

    public bool IsExpiredAt(DateTimeOffset now) => now >= ExpiresAtUtc;

    public bool IsActiveAt(DateTimeOffset now) => !IsRevoked && !IsExpiredAt(now);

    /// <summary>
    /// True when this token was already exchanged for another one. Presenting it again means
    /// two parties hold the same secret, and the whole family has to be assumed compromised.
    /// </summary>
    public bool WasRotated => ReplacedByTokenId is not null;

    public static RefreshToken Issue(
        Guid id,
        Guid userId,
        string tokenHash,
        string? deviceId,
        DateTimeOffset now,
        TimeSpan lifetime)
    {
        DomainException.Require(lifetime > TimeSpan.Zero, "A refresh token lifetime must be positive.");

        return new RefreshToken(
            id,
            userId,
            tokenHash,
            NormalizeDeviceId(deviceId),
            now.Add(lifetime),
            revokedAtUtc: null,
            RefreshTokenRevocationReason.None,
            replacedByTokenId: null,
            now,
            now);
    }

    public static RefreshToken FromStorage(
        Guid id,
        Guid userId,
        string tokenHash,
        string? deviceId,
        DateTimeOffset expiresAtUtc,
        DateTimeOffset? revokedAtUtc,
        RefreshTokenRevocationReason revocationReason,
        Guid? replacedByTokenId,
        DateTimeOffset createdAtUtc,
        DateTimeOffset updatedAtUtc) =>
        new(
            id,
            userId,
            tokenHash,
            deviceId,
            expiresAtUtc,
            revokedAtUtc,
            revocationReason,
            replacedByTokenId,
            createdAtUtc,
            updatedAtUtc);

    public void Revoke(DateTimeOffset now, RefreshTokenRevocationReason reason)
    {
        if (IsRevoked)
        {
            return;
        }

        DomainException.Require(
            reason != RefreshTokenRevocationReason.None,
            "Revoking a refresh token needs a reason.");

        RevokedAtUtc = now;
        RevocationReason = reason;
        Touch(now);
    }

    /// <summary>Retires this token in favour of <paramref name="replacement"/>.</summary>
    public void RotateTo(RefreshToken replacement, DateTimeOffset now)
    {
        DomainException.Require(
            replacement.UserId == UserId,
            "A refresh token can only be replaced by one belonging to the same user.");
        DomainException.Require(!IsRevoked, "A revoked refresh token cannot be rotated.");

        ReplacedByTokenId = replacement.Id;
        RevokedAtUtc = now;
        RevocationReason = RefreshTokenRevocationReason.Rotated;
        Touch(now);
    }

    private static string? NormalizeDeviceId(string? deviceId)
    {
        if (string.IsNullOrWhiteSpace(deviceId))
        {
            return null;
        }

        var trimmed = deviceId.Trim();
        return trimmed.Length <= 128 ? trimmed : trimmed[..128];
    }
}
