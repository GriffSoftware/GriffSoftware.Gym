using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Security;
using GriffGym.Domain.Users;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;

namespace GriffGym.Infrastructure.Security;

internal sealed class JwtAccessTokenIssuer(IOptions<JwtOptions> options, IClock clock)
    : IAccessTokenIssuer
{
    private readonly JwtOptions _options = options.Value;

    public AccessToken Issue(User user)
    {
        var now = clock.UtcNow;
        var lifetime = TimeSpan.FromMinutes(_options.AccessTokenMinutes);
        var expiresAt = now.Add(lifetime);

        var credentials = new SigningCredentials(
            new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_options.SigningKey)),
            SecurityAlgorithms.HmacSha256);

        var claims = new List<Claim>
        {
            // The subject is the only thing that decides whose data a request may touch. No
            // endpoint reads a user id from a route or a body.
            new(JwtRegisteredClaimNames.Sub, user.Id.ToString()),
            new(JwtRegisteredClaimNames.Email, user.Email.Value),
            new(JwtRegisteredClaimNames.Jti, Guid.CreateVersion7().ToString()),
            // Checked on every authenticated request. A token whose stamp no longer matches an
            // existing account is refused rather than honoured until it expires.
            new(GriffGymClaims.SecurityStamp, user.SecurityStamp),
        };

        var token = new JwtSecurityToken(
            issuer: _options.Issuer,
            audience: _options.Audience,
            claims: claims,
            notBefore: now.UtcDateTime,
            expires: expiresAt.UtcDateTime,
            signingCredentials: credentials);

        return new AccessToken(
            new JwtSecurityTokenHandler().WriteToken(token),
            expiresAt,
            lifetime);
    }
}
