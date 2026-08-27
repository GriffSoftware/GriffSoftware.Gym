using GriffGym.Domain.Users;
using GriffGym.Infrastructure.Persistence.Entities;

namespace GriffGym.Infrastructure.Persistence.Mappers;

/*
 * The domain model and the persistence model are different types on purpose. The domain one is
 * rich and validates itself; the persistence one is a flat bag of columns that EF Core knows
 * how to write. These mappers are the only place the two meet, which is what keeps EF
 * attributes, navigation properties and lazy-loading proxies out of the business rules.
 */

internal static class UserMapper
{
    public static User ToDomain(UserRecord record)
    {
        var user = User.FromStorage(
            record.Id,
            EmailAddress.FromStorage(record.Email, record.NormalizedEmail),
            record.PasswordHash,
            record.SecurityStamp,
            record.CreatedAtUtc,
            record.UpdatedAtUtc);

        user.ApplySyncMetadata(
            record.Version,
            record.SyncVersion,
            record.UpdatedAtUtc,
            record.DeletedAtUtc);

        return user;
    }

    public static UserRecord ToRecord(User user)
    {
        var record = new UserRecord { Id = user.Id };
        Apply(user, record);
        return record;
    }

    public static void Apply(User user, UserRecord record)
    {
        record.Email = user.Email.Value;
        record.NormalizedEmail = user.Email.Normalized;
        record.PasswordHash = user.PasswordHash;
        record.SecurityStamp = user.SecurityStamp;
        record.CreatedAtUtc = user.CreatedAtUtc;
        record.DeletedAtUtc = user.DeletedAtUtc;
    }
}

internal static class RefreshTokenMapper
{
    public static RefreshToken ToDomain(RefreshTokenRecord record) => RefreshToken.FromStorage(
        record.Id,
        record.UserId,
        record.TokenHash,
        record.DeviceId,
        record.ExpiresAtUtc,
        record.RevokedAtUtc,
        record.RevocationReason,
        record.ReplacedByTokenId,
        record.CreatedAtUtc,
        record.UpdatedAtUtc);

    public static RefreshTokenRecord ToRecord(RefreshToken token)
    {
        var record = new RefreshTokenRecord
        {
            Id = token.Id,
            UserId = token.UserId,
            TokenHash = token.TokenHash,
            DeviceId = token.DeviceId,
            ExpiresAtUtc = token.ExpiresAtUtc,
            CreatedAtUtc = token.CreatedAtUtc,
        };

        Apply(token, record);

        return record;
    }

    public static void Apply(RefreshToken token, RefreshTokenRecord record)
    {
        record.RevokedAtUtc = token.RevokedAtUtc;
        record.RevocationReason = token.RevocationReason;
        record.ReplacedByTokenId = token.ReplacedByTokenId;
        record.UpdatedAtUtc = token.UpdatedAtUtc;
    }
}
