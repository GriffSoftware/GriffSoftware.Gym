using GriffGym.Application.Abstractions.Security;
using GriffGym.Application.Common;
using Google.Apis.Auth;
using Microsoft.Extensions.Options;

namespace GriffGym.Infrastructure.Security;

internal sealed class GoogleIdTokenValidator(IOptions<GoogleOptions> options) : IGoogleIdTokenValidator
{
    public async Task<GoogleIdentity> ValidateAsync(string idToken, CancellationToken cancellationToken)
    {
        var webClientId = options.Value.WebClientId;

        if (string.IsNullOrWhiteSpace(webClientId))
        {
            // Not a startup failure (see GoogleOptions) — but this specific request has nothing
            // to check the token's audience against, so it cannot proceed either.
            throw new AuthenticationFailedException("Google sign-in is not configured on this server.");
        }

        GoogleJsonWebSignature.Payload payload;
        try
        {
            payload = await GoogleJsonWebSignature.ValidateAsync(
                idToken,
                new GoogleJsonWebSignature.ValidationSettings { Audience = [webClientId] });
        }
        catch (InvalidJwtException)
        {
            throw new AuthenticationFailedException("Invalid Google credential.");
        }

        return new GoogleIdentity(payload.Subject, payload.Email, payload.EmailVerified);
    }
}
