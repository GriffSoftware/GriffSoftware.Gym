using GriffGym.Application.Common;
using GriffGym.Infrastructure.Security;
using Microsoft.Extensions.Options;

namespace GriffGym.Infrastructure.Tests;

public sealed class GoogleIdTokenValidatorTests
{
    [Fact]
    public async Task Rejects_a_string_that_is_not_even_shaped_like_a_jwt()
    {
        // Regression test: Google.Apis.Auth does not confine every way of rejecting a bad
        // token to InvalidJwtException — a string with no JWT structure at all throws
        // something else while parsing it, before validation proper ever runs. That used to
        // reach production as an unhandled 500; it must always surface as 401 instead.
        var validator = new GoogleIdTokenValidator(
            Options.Create(new GoogleOptions { WebClientId = "test-web-client-id" }));

        await Assert.ThrowsAsync<AuthenticationFailedException>(
            () => validator.ValidateAsync("clearly-not-a-real-google-token", default));
    }

    [Fact]
    public async Task Refuses_when_no_web_client_id_is_configured()
    {
        var validator = new GoogleIdTokenValidator(
            Options.Create(new GoogleOptions { WebClientId = string.Empty }));

        await Assert.ThrowsAsync<AuthenticationFailedException>(
            () => validator.ValidateAsync("anything", default));
    }
}
