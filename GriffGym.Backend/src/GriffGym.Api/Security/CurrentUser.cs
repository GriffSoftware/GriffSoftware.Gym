using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using GriffGym.Application.Abstractions;

namespace GriffGym.Api.Security;

/// <summary>
/// Who is making this request, read from the validated access token and from nothing else.
///
/// This is the single place ownership originates. No route value, query string or request body
/// can name a different user, so forging identity means forging a signature.
/// </summary>
internal sealed class CurrentUser(IHttpContextAccessor accessor) : ICurrentUser
{
    public Guid? UserId
    {
        get
        {
            var principal = accessor.HttpContext?.User;

            if (principal?.Identity?.IsAuthenticated != true)
            {
                return null;
            }

            // ASP.NET Core rewrites "sub" to ClaimTypes.NameIdentifier under the default inbound
            // claim mapping, so both spellings are accepted rather than depending on whether
            // that mapping happens to be switched off.
            var raw = principal.FindFirstValue(ClaimTypes.NameIdentifier)
                      ?? principal.FindFirstValue(JwtRegisteredClaimNames.Sub);

            return Guid.TryParse(raw, out var userId) ? userId : null;
        }
    }

    public bool IsAuthenticated => UserId is not null;
}
