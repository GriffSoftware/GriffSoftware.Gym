using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Abstractions.Security;
using Microsoft.AspNetCore.Authentication.JwtBearer;

namespace GriffGym.Api.Security;

/// <summary>
/// The step that turns "this token has a valid signature" into "this token still names a real
/// account".
///
/// A JWT is a signed statement frozen at the moment it was minted, and everything the framework
/// checks — signature, issuer, audience, expiry — is a property of that frozen statement. None
/// of it notices that the account has since been deleted. Without this, a lifter who deletes
/// their account keeps a working credential for the remainder of the access token's fifteen
/// minutes, and every endpoint that does not happen to load the <c>user</c> row would go on
/// answering with data that was supposed to be gone. For a feature whose entire purpose is that
/// the data is gone, that is not an acceptable window.
///
/// So: one indexed lookup per authenticated request, reading a single column. That is a real
/// cost on the hot path and it is deliberately paid. The claim is checked as well as the row's
/// existence, so rotating a security stamp — a password change, a future "sign out everywhere
/// and mean it" — retires tokens already in the wild by the same mechanism.
///
/// <see cref="TokenValidatedContext.Fail(string)"/> rather than an exception: a token that no
/// longer stands for anything is an unauthenticated request, and the JWT handler already turns
/// that into a <c>401</c> with the standard problem shape.
/// </summary>
internal static class AccessTokenValidation
{
    public static async Task EnsureAccountIsStillActiveAsync(TokenValidatedContext context)
    {
        var principal = context.Principal;

        // Both spellings, for the same reason CurrentUser accepts both: inbound claim mapping
        // is switched off here, but nothing about this check should depend on that staying so.
        var subject = principal?.FindFirstValue(ClaimTypes.NameIdentifier)
                      ?? principal?.FindFirstValue(JwtRegisteredClaimNames.Sub);

        if (!Guid.TryParse(subject, out var userId))
        {
            context.Fail("The access token does not identify an account.");
            return;
        }

        var users = context.HttpContext.RequestServices.GetRequiredService<IUserRepository>();

        var currentStamp = await users.FindSecurityStampAsync(
            userId,
            context.HttpContext.RequestAborted);

        if (currentStamp is null)
        {
            context.Fail("The account this access token was issued for no longer exists.");
            return;
        }

        var presentedStamp = principal!.FindFirstValue(GriffGymClaims.SecurityStamp);

        // Ordinal, and non-empty. A token minted before the claim existed would present null,
        // and treating that as a match would leave the check switched off for exactly the
        // tokens that predate it.
        if (string.IsNullOrEmpty(presentedStamp)
            || !string.Equals(presentedStamp, currentStamp, StringComparison.Ordinal))
        {
            context.Fail("This access token has been superseded. Sign in again.");
        }
    }
}
