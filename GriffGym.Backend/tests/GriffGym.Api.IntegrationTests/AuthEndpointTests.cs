using System.Net;
using System.Net.Http.Json;
using GriffGym.Api.Contracts.V1;
using GriffGym.TestSupport;

namespace GriffGym.Api.IntegrationTests;

public sealed class AuthEndpointTests(PostgresFixture fixture) : ApiTest(fixture)
{
    [Fact]
    public async Task Register_creates_an_account_and_returns_a_token_pair()
    {
        var client = CreateClient();

        var response = await client.PostAsJsonAsync(
            "/api/v1/auth/register",
            new RegisterRequest("new@example.com", "correct horse battery", "pixel-9"),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Created, response.StatusCode);

        var body = await response.ReadAsync<AuthenticationResponse>();
        Assert.Equal("new@example.com", body.Email);
        Assert.Equal("Bearer", body.TokenType);
        Assert.NotEmpty(body.AccessToken);
        Assert.NotEmpty(body.RefreshToken);
        Assert.True(body.ExpiresInSeconds > 0);
    }

    [Fact]
    public async Task Register_refuses_an_address_that_already_has_an_account()
    {
        await RegisterLifterAsync("taken@example.com");
        var client = CreateClient();

        var response = await client.PostAsJsonAsync(
            "/api/v1/auth/register",
            new RegisterRequest("TAKEN@example.com", "another password", null),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
    }

    [Theory]
    [InlineData("not-an-email", "correct horse battery")]
    [InlineData("fine@example.com", "short")]
    [InlineData("", "correct horse battery")]
    public async Task Register_rejects_a_payload_that_does_not_validate(string email, string password)
    {
        var client = CreateClient();

        var response = await client.PostAsJsonAsync(
            "/api/v1/auth/register",
            new RegisterRequest(email, password, null),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);

        // One consistent error shape across the whole API.
        var problem = await response.Content.ReadAsStringAsync();
        Assert.Contains("\"errors\"", problem, StringComparison.Ordinal);
        Assert.Contains("Validation failed", problem, StringComparison.Ordinal);
    }

    [Fact]
    public async Task Login_returns_a_new_pair_for_the_right_password()
    {
        await RegisterLifterAsync("login@example.com");
        var client = CreateClient();

        var response = await client.PostAsJsonAsync(
            "/api/v1/auth/login",
            new LoginRequest("login@example.com", "correct horse battery", "tablet"),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.NotEmpty((await response.ReadAsync<AuthenticationResponse>()).AccessToken);
    }

    [Fact]
    public async Task Login_refuses_a_wrong_password()
    {
        await RegisterLifterAsync("login2@example.com");
        var client = CreateClient();

        var response = await client.PostAsJsonAsync(
            "/api/v1/auth/login",
            new LoginRequest("login2@example.com", "wrong", null),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Login_answers_the_same_way_for_an_account_that_does_not_exist()
    {
        var client = CreateClient();

        var response = await client.PostAsJsonAsync(
            "/api/v1/auth/login",
            new LoginRequest("nobody@example.com", "correct horse battery", null),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    /// <summary>
    /// Exercises the real host and its real DI container — the unit tests around
    /// <c>GoogleLoginUseCase</c> construct it by hand and would never have caught the endpoint
    /// itself failing to resolve it. This test previously reproduced exactly that: an
    /// unregistered <c>GoogleLoginUseCase</c> made every call here a 500, not a 401.
    /// </summary>
    [Fact]
    public async Task Google_login_rejects_a_bogus_token_without_crashing()
    {
        var client = CreateClient();

        var response = await client.PostAsJsonAsync(
            "/api/v1/auth/google",
            new GoogleLoginRequest("clearly-not-a-real-google-token", null),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Refresh_rotates_the_token_and_the_old_one_stops_working()
    {
        var lifter = await RegisterLifterAsync();
        var client = CreateClient();

        var refreshed = await (await client.PostAsJsonAsync(
                "/api/v1/auth/refresh",
                new RefreshRequest(lifter.Credentials.RefreshToken, null),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<AuthenticationResponse>();

        Assert.NotEqual(lifter.Credentials.RefreshToken, refreshed.RefreshToken);

        var replayed = await client.PostAsJsonAsync(
            "/api/v1/auth/refresh",
            new RefreshRequest(lifter.Credentials.RefreshToken, null),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Unauthorized, replayed.StatusCode);
    }

    [Fact]
    public async Task Reusing_a_rotated_token_revokes_every_session()
    {
        // Two parties hold the same secret; which one is the thief is unknowable.
        var lifter = await RegisterLifterAsync("reuse@example.com");
        var client = CreateClient();

        var secondDevice = await (await client.PostAsJsonAsync(
                "/api/v1/auth/login",
                new LoginRequest("reuse@example.com", "correct horse battery", "tablet"),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<AuthenticationResponse>();

        var rotated = await (await client.PostAsJsonAsync(
                "/api/v1/auth/refresh",
                new RefreshRequest(lifter.Credentials.RefreshToken, null),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<AuthenticationResponse>();

        // The replay is what trips the alarm.
        var replay = await client.PostAsJsonAsync(
            "/api/v1/auth/refresh",
            new RefreshRequest(lifter.Credentials.RefreshToken, null),
            GriffGymApiFactory.Json);
        Assert.Equal(HttpStatusCode.Unauthorized, replay.StatusCode);

        foreach (var token in new[] { rotated.RefreshToken, secondDevice.RefreshToken })
        {
            var response = await client.PostAsJsonAsync(
                "/api/v1/auth/refresh",
                new RefreshRequest(token, null),
                GriffGymApiFactory.Json);

            Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        }
    }

    [Fact]
    public async Task Logout_revokes_the_refresh_token_it_is_given()
    {
        var lifter = await RegisterLifterAsync();
        var client = CreateClient();

        var logout = await client.PostAsJsonAsync(
            "/api/v1/auth/logout",
            new LogoutRequest(lifter.Credentials.RefreshToken),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.NoContent, logout.StatusCode);

        var refresh = await client.PostAsJsonAsync(
            "/api/v1/auth/refresh",
            new RefreshRequest(lifter.Credentials.RefreshToken, null),
            GriffGymApiFactory.Json);

        Assert.Equal(HttpStatusCode.Unauthorized, refresh.StatusCode);
    }

    [Fact]
    public async Task Logout_all_ends_every_device()
    {
        var lifter = await RegisterLifterAsync("everywhere@example.com");
        var anonymous = CreateClient();

        var tablet = await (await anonymous.PostAsJsonAsync(
                "/api/v1/auth/login",
                new LoginRequest("everywhere@example.com", "correct horse battery", "tablet"),
                GriffGymApiFactory.Json))
            .ReadSuccessAsync<AuthenticationResponse>();

        var response = await lifter.Client.PostAsync("/api/v1/auth/logout-all", null);
        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);

        foreach (var token in new[] { lifter.Credentials.RefreshToken, tablet.RefreshToken })
        {
            var refresh = await anonymous.PostAsJsonAsync(
                "/api/v1/auth/refresh",
                new RefreshRequest(token, null),
                GriffGymApiFactory.Json);

            Assert.Equal(HttpStatusCode.Unauthorized, refresh.StatusCode);
        }
    }

    [Fact]
    public async Task Users_me_returns_the_signed_in_lifter()
    {
        var lifter = await RegisterLifterAsync("me@example.com");

        var me = await (await lifter.Client.GetAsync("/api/v1/users/me"))
            .ReadSuccessAsync<UserResponse>();

        Assert.Equal(lifter.Id, me.Id);
        Assert.Equal("me@example.com", me.Email);
    }

    [Theory]
    [InlineData("/api/v1/users/me")]
    [InlineData("/api/v1/reference-maxes")]
    [InlineData("/api/v1/exercises")]
    [InlineData("/api/v1/cycles")]
    [InlineData("/api/v1/workouts")]
    [InlineData("/api/v1/workouts/active")]
    [InlineData("/api/v1/state")]
    public async Task Every_data_endpoint_refuses_an_unauthenticated_caller(string path)
    {
        var response = await CreateClient().GetAsync(path);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task A_forged_token_is_refused()
    {
        var client = CreateClient();
        client.DefaultRequestHeaders.Authorization =
            new System.Net.Http.Headers.AuthenticationHeaderValue(
                "Bearer",
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEifQ.not-a-real-signature");

        var response = await client.GetAsync("/api/v1/users/me");

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }
}

/// <summary>
/// Its own host, because the limit has to be lowered to something a test can reach without
/// sending ten thousand requests.
/// </summary>
[Collection(ApiCollection.Name)]
public sealed class RateLimitTests(PostgresFixture fixture) : IAsyncLifetime
{
    private GriffGymApiFactory? _factory;

    public async ValueTask InitializeAsync()
    {
        Assert.SkipUnless(fixture.IsAvailable, fixture.SkipReason);

        await fixture.ResetAsync();
        _factory = new GriffGymApiFactory(fixture.ConnectionString)
        {
            AuthenticationPermitsPerMinute = 3,
        };
    }

    [Fact]
    public async Task Login_attempts_are_capped()
    {
        var client = _factory!.CreateClient();
        var statuses = new List<HttpStatusCode>();

        for (var attempt = 0; attempt < 6; attempt++)
        {
            var response = await client.PostAsJsonAsync(
                "/api/v1/auth/login",
                new LoginRequest("nobody@example.com", "guessing", null),
                GriffGymApiFactory.Json);

            statuses.Add(response.StatusCode);
        }

        Assert.Contains(HttpStatusCode.TooManyRequests, statuses);
        Assert.Equal(3, statuses.Count(status => status == HttpStatusCode.Unauthorized));
    }

    public async ValueTask DisposeAsync()
    {
        GC.SuppressFinalize(this);

        if (_factory is not null)
        {
            await _factory.DisposeAsync();
        }
    }
}
