using GriffGym.Domain.Users;

namespace GriffGym.Infrastructure.Persistence.Entities;

internal sealed class UserRecord : ISyncable
{
    public Guid Id { get; set; }

    public string Email { get; set; } = string.Empty;

    /// <summary>Upper-invariant. The unique index is on this, so casing cannot fork an account.</summary>
    public string NormalizedEmail { get; set; } = string.Empty;

    public string PasswordHash { get; set; } = string.Empty;

    public string SecurityStamp { get; set; } = string.Empty;

    public string? GoogleSubjectId { get; set; }

    public int Version { get; set; }

    public long SyncVersion { get; set; }

    public DateTimeOffset CreatedAtUtc { get; set; }

    public DateTimeOffset UpdatedAtUtc { get; set; }

    public DateTimeOffset? DeletedAtUtc { get; set; }

    public ICollection<RefreshTokenRecord> RefreshTokens { get; set; } = [];
}

/// <summary>
/// Deliberately not <see cref="ISyncable"/>: a refresh token is a credential, never something a
/// phone downloads. Only its hash is stored, so a leaked backup yields nothing usable.
/// </summary>
internal sealed class RefreshTokenRecord
{
    public Guid Id { get; set; }

    public Guid UserId { get; set; }

    public string TokenHash { get; set; } = string.Empty;

    public string? DeviceId { get; set; }

    public DateTimeOffset ExpiresAtUtc { get; set; }

    public DateTimeOffset? RevokedAtUtc { get; set; }

    public RefreshTokenRevocationReason RevocationReason { get; set; }

    public Guid? ReplacedByTokenId { get; set; }

    public DateTimeOffset CreatedAtUtc { get; set; }

    public DateTimeOffset UpdatedAtUtc { get; set; }

    public UserRecord? User { get; set; }
}
