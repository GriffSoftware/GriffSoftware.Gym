using GriffGym.Application.Common;

namespace GriffGym.Application.Abstractions;

/// <summary>
/// Who is making this request, taken from the validated access token and nothing else.
///
/// No endpoint accepts a user id in a route, query string or body as a way of saying whose
/// data is wanted. Ownership is derived here, so forging it would mean forging a signed token.
/// </summary>
public interface ICurrentUser
{
    Guid? UserId { get; }

    bool IsAuthenticated { get; }
}

public static class CurrentUserExtensions
{
    public static Guid RequireUserId(this ICurrentUser currentUser) =>
        currentUser.UserId ?? throw new UnauthenticatedException();
}
