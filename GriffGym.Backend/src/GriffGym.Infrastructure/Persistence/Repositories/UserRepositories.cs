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

    public Task<bool> EmailExistsAsync(string normalizedEmail, CancellationToken cancellationToken) =>
        context.Set<UserRecord>()
            .AnyAsync(
                user => user.NormalizedEmail == normalizedEmail && user.DeletedAtUtc == null,
                cancellationToken);

    public void Add(User user)
    {
        var record = UserMapper.ToRecord(user);
        context.Set<UserRecord>().Add(record);
        Track(user, record);
    }
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
}
