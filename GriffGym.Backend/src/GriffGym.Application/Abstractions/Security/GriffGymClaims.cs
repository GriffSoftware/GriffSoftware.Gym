namespace GriffGym.Application.Abstractions.Security;

/// <summary>
/// The non-standard claims this API puts in an access token.
///
/// It lives in the Application layer rather than next to the JWT issuer because the claim is
/// written in Infrastructure and read at the API boundary, and those two assemblies cannot see
/// each other's internals. A literal spelled out twice is a claim that stops being checked the
/// day somebody fixes a typo in one of them.
/// </summary>
public static class GriffGymClaims
{
    /// <summary>
    /// The user's security stamp at the moment the token was minted.
    ///
    /// An access token is a signed statement about an account, and nothing about it is looked
    /// up again once it is signed. This claim is what lets the boundary ask "is that statement
    /// still true?" — a deleted account has no stamp to match, and a rotated stamp retires
    /// every token issued before it, without waiting out the fifteen-minute lifetime.
    /// </summary>
    public const string SecurityStamp = "sstamp";
}
