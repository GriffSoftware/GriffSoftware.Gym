using GriffGym.Api.Contracts.V1;
using GriffGym.Application.Auth;
using GriffGym.Application.Common;
using GriffGym.Application.Users;

namespace GriffGym.Api.Mapping;

/*
 * Translation between the HTTP contract and the application's own commands and read models.
 *
 * The two are usually close in shape, and that is fine: the value is not in them differing, it
 * is in being able to change one without the other. A v2 of this API changes files in this
 * folder; the use cases do not move.
 */

internal static class AuthMapping
{
    public static RegisterUserCommand ToCommand(this RegisterRequest request) =>
        new(request.Email, request.Password, request.DeviceId);

    public static LoginUserCommand ToCommand(this LoginRequest request) =>
        new(request.Email, request.Password, request.DeviceId);

    public static RefreshTokenCommand ToCommand(this RefreshRequest request) =>
        new(request.RefreshToken, request.DeviceId);

    public static LogoutCommand ToCommand(this LogoutRequest request) => new(request.RefreshToken);

    public static AuthenticationResponse ToResponse(this AuthenticationResult result) => new(
        result.UserId,
        result.Email,
        result.AccessToken,
        "Bearer",
        result.AccessTokenExpiresAtUtc,
        result.AccessTokenExpiresInSeconds,
        result.RefreshToken,
        result.RefreshTokenExpiresAtUtc);

    public static UserResponse ToResponse(this UserProfile profile) =>
        new(profile.Id, profile.Email, profile.CreatedAtUtc, profile.UpdatedAtUtc);
}

internal static class PagingMapping
{
    public static PagedResponse<TResponse> ToResponse<TItem, TResponse>(
        this PagedResult<TItem> page,
        Func<TItem, TResponse> map) =>
        new(
            [.. page.Items.Select(map)],
            page.Page,
            page.PageSize,
            page.TotalCount,
            page.TotalPages,
            page.HasNextPage);
}
