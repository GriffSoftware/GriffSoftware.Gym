using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Common;

namespace GriffGym.Application.Users;

public sealed record UserProfile(
    Guid Id,
    string Email,
    DateTimeOffset CreatedAtUtc,
    DateTimeOffset UpdatedAtUtc);

/// <summary>
/// Reads the signed-in lifter's own profile. There is no "get user by id" endpoint, because
/// there is no legitimate reason for one account to read another.
/// </summary>
public sealed class GetCurrentUserUseCase(IUserRepository users, ICurrentUser currentUser)
{
    public async Task<UserProfile> ExecuteAsync(CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();
        var user = await users.FindByIdAsync(userId, cancellationToken)
                   ?? throw new NotFoundException("User", userId);

        return new UserProfile(user.Id, user.Email.Value, user.CreatedAtUtc, user.UpdatedAtUtc);
    }
}
