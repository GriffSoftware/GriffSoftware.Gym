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
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            // Deliberately broad: idToken is an arbitrary, attacker-controlled string at this
            // point, and Google's library does not confine every way of rejecting one to
            // InvalidJwtException — a string that isn't even JWT-shaped throws something else
            // while parsing it, before validation proper ever runs. Anything short of
            // cancellation means the same thing here: not a usable credential, never a 500.
            throw new AuthenticationFailedException("Invalid Google credential.");
        }

        return new GoogleIdentity(payload.Subject, payload.Email, payload.EmailVerified);
    }
}
