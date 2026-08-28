using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Domain.Users;
using GriffGym.Infrastructure.Persistence.Entities;
using GriffGym.Infrastructure.Persistence.Mappers;
using Microsoft.EntityFrameworkCore;

namespace GriffGym.Infrastructure.Persistence.Repositories;

internal sealed class UserRepository(GriffGymDbContext context)
    : TrackedRepository<User, UserRecord>, IUserRepository
{
    protected override void Apply(User domain, UserRecord record) =>
        UserMapper.Apply(domain, record);

    public async Task<User?> FindByIdAsync(Guid id, CancellationToken cancellationToken)
    {
        if (Cached(id) is { } cached)
        {
            return cached;
        }

        var record = await context.Set<UserRecord>()
            .FirstOrDefaultAsync(
                user => user.Id == id && user.DeletedAtUtc == null,
                cancellationToken);

        return record is null ? null : Materialise(record, record.Id, UserMapper.ToDomain);
    }

    public async Task<User?> FindByNormalizedEmailAsync(
        string normalizedEmail,
        CancellationToken cancellationToken)
    {
        var record = await context.Set<UserRecord>()
            .FirstOrDefaultAsync(
                user => user.NormalizedEmail == normalizedEmail && user.DeletedAtUtc == null,
                cancellationToken);

        return record is null ? null : Materialise(record, record.Id, UserMapper.ToDomain);
    }

    public async Task<User?> FindByGoogleSubjectIdAsync(
        string googleSubjectId,
        CancellationToken cancellationToken)
    {
        var record = await context.Set<UserRecord>()
            .FirstOrDefaultAsync(
                user => user.GoogleSubjectId == googleSubjectId && user.DeletedAtUtc == null,
                cancellationToken);

        return record is null ? null : Materialise(record, record.Id, UserMapper.ToDomain);
    }

    public Task<bool> EmailExistsAsync(string normalizedEmail, CancellationToken cancellationToken) =>
        context.Set<UserRecord>()
            .AnyAsync(
                user => user.NormalizedEmail == normalizedEmail && user.DeletedAtUtc == null,
                cancellationToken);

    public Task<string?> FindSecurityStampAsync(Guid id, CancellationToken cancellationToken) =>
        context.Set<UserRecord>()
            .AsNoTracking()
            .Where(user => user.Id == id && user.DeletedAtUtc == null)
            .Select(user => (string?)user.SecurityStamp)
            .FirstOrDefaultAsync(cancellationToken);

    public void Add(User user)
    {
        var record = UserMapper.ToRecord(user);
        context.Set<UserRecord>().Add(record);
        Track(user, record);
    }

    public async Task<bool> DeleteAsync(Guid id, CancellationToken cancellationToken) =>
        await context.Set<UserRecord>()
            .Where(user => user.Id == id)
            .ExecuteDeleteAsync(cancellationToken) > 0;
}

internal sealed class RefreshTokenRepository(GriffGymDbContext context)
    : TrackedRepository<RefreshToken, RefreshTokenRecord>, IRefreshTokenRepository
{
    protected override void Apply(RefreshToken domain, RefreshTokenRecord record) =>
        RefreshTokenMapper.Apply(domain, record);

    public async Task<RefreshToken?> FindByHashAsync(
        string tokenHash,
        CancellationToken cancellationToken)
    {
        var record = await context.Set<RefreshTokenRecord>()
            .FirstOrDefaultAsync(token => token.TokenHash == tokenHash, cancellationToken);

        return record is null ? null : Materialise(record, record.Id, RefreshTokenMapper.ToDomain);
    }

    public async Task<IReadOnlyList<RefreshToken>> ListActiveForUserAsync(
        Guid userId,
        DateTimeOffset now,
        CancellationToken cancellationToken)
    {
        var records = await context.Set<RefreshTokenRecord>()
            .Where(token =>
                token.UserId == userId
                && token.RevokedAtUtc == null
                && token.ExpiresAtUtc > now)
            .ToListAsync(cancellationToken);

        return [.. records.Select(record =>
            Materialise(record, record.Id, RefreshTokenMapper.ToDomain))];
    }

    public void Add(RefreshToken token)
    {
        var record = RefreshTokenMapper.ToRecord(token);
        context.Set<RefreshTokenRecord>().Add(record);
        Track(token, record);
    }

    /// <summary>
    /// Every session, not just the live ones: a revoked or expired row still records that this
    /// account existed and which devices it was used from.
    /// </summary>
    public Task<int> DeleteAllForUserAsync(Guid userId, CancellationToken cancellationToken) =>
        context.Set<RefreshTokenRecord>()
            .Where(token => token.UserId == userId)
            .ExecuteDeleteAsync(cancellationToken);
}
