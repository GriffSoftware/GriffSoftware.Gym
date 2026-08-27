using GriffGym.Application.Auth;
using GriffGym.Application.Common;
using GriffGym.Domain.Users;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;

namespace GriffGym.Application.Tests;

/// <summary>Everything the authentication use cases need, wired the way the container wires it.</summary>
internal sealed class AuthHarness
{
    public AuthHarness()
    {
        Clock = new FakeClock(new DateTimeOffset(2026, 4, 1, 8, 0, 0, TimeSpan.Zero));
        Sessions = new AuthenticationSessionService(
            RefreshTokens,
            new FakeAccessTokenIssuer(Clock),
            TokenGenerator,
            Identifiers,
            Clock,
            Options.Create(new AuthenticationSettings
            {
                AccessTokenLifetime = TimeSpan.FromMinutes(15),
                RefreshTokenLifetime = TimeSpan.FromDays(30),
            }));
    }

    public FakeClock Clock { get; }

    public FakeUserRepository Users { get; } = new();

    public FakeRefreshTokenRepository RefreshTokens { get; } = new();

    public FakeUnitOfWork UnitOfWork { get; } = new();

    public FakePasswordHasher Hasher { get; } = new();

    public FakeRefreshTokenGenerator TokenGenerator { get; } = new();

    public FakeIdentifierFactory Identifiers { get; } = new();

    public FakeCurrentUser CurrentUser { get; } = new();

    public AuthenticationSessionService Sessions { get; }

    public RegisterUserUseCase Register => new(
        Users, UnitOfWork, Hasher, Sessions, Identifiers, Clock,
        NullLogger<RegisterUserUseCase>.Instance);

    public LoginUserUseCase Login => new(
        Users, UnitOfWork, Hasher, Sessions, Clock, NullLogger<LoginUserUseCase>.Instance);

    public RefreshTokenUseCase Refresh => new(
        Users, RefreshTokens, UnitOfWork, TokenGenerator, Sessions, Clock,
        NullLogger<RefreshTokenUseCase>.Instance);

    public LogoutUserUseCase Logout => new(
        RefreshTokens, UnitOfWork, TokenGenerator, Clock, NullLogger<LogoutUserUseCase>.Instance);

    public LogoutAllSessionsUseCase LogoutAll => new(
        RefreshTokens, UnitOfWork, CurrentUser, Clock,
        NullLogger<LogoutAllSessionsUseCase>.Instance);

    public async Task<AuthenticationResult> RegisterAsync(
        string email = "lifter@example.com",
        string password = "correct horse battery",
        string? deviceId = "pixel-9") =>
        await Register.ExecuteAsync(new RegisterUserCommand(email, password, deviceId), default);
}

public sealed class RegisterUserUseCaseTests
{
    [Fact]
    public async Task Creates_the_account_and_signs_it_straight_in()
    {
        var harness = new AuthHarness();

        var result = await harness.RegisterAsync();

        var user = Assert.Single(harness.Users.All);
        Assert.Equal("lifter@example.com", user.Email.Value);
        Assert.Equal("LIFTER@EXAMPLE.COM", user.Email.Normalized);
        Assert.Equal("hashed:correct horse battery", user.PasswordHash);

        Assert.Equal(user.Id, result.UserId);
        Assert.NotEmpty(result.AccessToken);
        Assert.NotEmpty(result.RefreshToken);
        Assert.Equal(1, harness.UnitOfWork.SaveCount);
    }

    [Fact]
    public async Task Stores_only_a_hash_of_the_refresh_token()
    {
        // The plaintext goes to the client once and is never written down.
        var harness = new AuthHarness();

        var result = await harness.RegisterAsync();

        var stored = Assert.Single(harness.RefreshTokens.All);
        Assert.NotEqual(result.RefreshToken, stored.TokenHash);
        Assert.Equal(harness.TokenGenerator.HashPresented(result.RefreshToken), stored.TokenHash);
    }

    [Fact]
    public async Task Refuses_an_address_that_already_has_an_account()
    {
        var harness = new AuthHarness();
        await harness.RegisterAsync("lifter@example.com");

        // Case-folded: the same account, however it was typed.
        await Assert.ThrowsAsync<ConflictException>(
            () => harness.RegisterAsync("LIFTER@Example.com"));
    }

    [Fact]
    public async Task Rejects_an_address_that_is_not_an_address()
    {
        var harness = new AuthHarness();

        await Assert.ThrowsAsync<GriffGym.Domain.Common.DomainException>(
            () => harness.RegisterAsync("not-an-email"));
    }
}

public sealed class LoginUserUseCaseTests
{
    [Fact]
    public async Task Issues_a_fresh_pair_for_the_right_password()
    {
        var harness = new AuthHarness();
        await harness.RegisterAsync();

        var result = await harness.Login.ExecuteAsync(
            new LoginUserCommand("lifter@example.com", "correct horse battery", "tablet"),
            default);

        Assert.NotEmpty(result.AccessToken);
        Assert.Equal(2, harness.RefreshTokens.All.Count);
        Assert.Contains(harness.RefreshTokens.All, token => token.DeviceId == "tablet");
    }

    [Fact]
    public async Task Rejects_a_wrong_password()
    {
        var harness = new AuthHarness();
        await harness.RegisterAsync();

        await Assert.ThrowsAsync<AuthenticationFailedException>(() => harness.Login.ExecuteAsync(
            new LoginUserCommand("lifter@example.com", "wrong", null),
            default));
    }

    [Fact]
    public async Task Answers_the_same_way_for_an_account_that_does_not_exist()
    {
        // Same exception, same message: login must not become a way to find out who has an
        // account here.
        var harness = new AuthHarness();
        await harness.RegisterAsync();

        var unknown = await Assert.ThrowsAsync<AuthenticationFailedException>(
            () => harness.Login.ExecuteAsync(
                new LoginUserCommand("nobody@example.com", "correct horse battery", null),
                default));

        var wrongPassword = await Assert.ThrowsAsync<AuthenticationFailedException>(
            () => harness.Login.ExecuteAsync(
                new LoginUserCommand("lifter@example.com", "wrong", null),
                default));

        Assert.Equal(unknown.Message, wrongPassword.Message);
    }

    [Fact]
    public async Task Upgrades_a_hash_produced_with_older_parameters()
    {
        // How a work-factor bump reaches accounts whose owners never change their password.
        var harness = new AuthHarness();
        await harness.RegisterAsync();

        harness.Hasher.ReportRehashNeeded = true;
        var stampBefore = harness.Users.All[0].SecurityStamp;

        await harness.Login.ExecuteAsync(
            new LoginUserCommand("lifter@example.com", "correct horse battery", null),
            default);

        // Re-hashing is not a credential change, so tokens already issued stay valid.
        Assert.Equal(stampBefore, harness.Users.All[0].SecurityStamp);
    }
}

public sealed class RefreshTokenUseCaseTests
{
    [Fact]
    public async Task Rotates_the_token_it_was_given()
    {
        var harness = new AuthHarness();
        var registered = await harness.RegisterAsync();

        harness.Clock.Advance(TimeSpan.FromMinutes(20));

        var refreshed = await harness.Refresh.ExecuteAsync(
            new RefreshTokenCommand(registered.RefreshToken, null),
            default);

        Assert.NotEqual(registered.RefreshToken, refreshed.RefreshToken);

        var original = harness.RefreshTokens.All[0];
        Assert.True(original.IsRevoked);
        Assert.True(original.WasRotated);
        Assert.Equal(RefreshTokenRevocationReason.Rotated, original.RevocationReason);
    }

    [Fact]
    public async Task Keeps_the_device_a_session_belongs_to()
    {
        var harness = new AuthHarness();
        var registered = await harness.RegisterAsync(deviceId: "pixel-9");

        await harness.Refresh.ExecuteAsync(
            new RefreshTokenCommand(registered.RefreshToken, null),
            default);

        Assert.All(harness.RefreshTokens.All, token => Assert.Equal("pixel-9", token.DeviceId));
    }

    [Fact]
    public async Task Refuses_a_token_that_was_already_used()
    {
        var harness = new AuthHarness();
        var registered = await harness.RegisterAsync();

        await harness.Refresh.ExecuteAsync(
            new RefreshTokenCommand(registered.RefreshToken, null),
            default);

        await Assert.ThrowsAsync<AuthenticationFailedException>(() => harness.Refresh.ExecuteAsync(
            new RefreshTokenCommand(registered.RefreshToken, null),
            default));
    }

    [Fact]
    public async Task Reuse_revokes_every_session_the_lifter_has()
    {
        // Two parties hold the same secret. Which one is the thief is unknowable, so both are
        // signed out and the lifter authenticates again.
        var harness = new AuthHarness();
        var first = await harness.RegisterAsync();
        var second = await harness.Login.ExecuteAsync(
            new LoginUserCommand("lifter@example.com", "correct horse battery", "tablet"),
            default);

        await harness.Refresh.ExecuteAsync(new RefreshTokenCommand(first.RefreshToken, null), default);

        await Assert.ThrowsAsync<AuthenticationFailedException>(() => harness.Refresh.ExecuteAsync(
            new RefreshTokenCommand(first.RefreshToken, null),
            default));

        Assert.All(
            harness.RefreshTokens.All,
            token => Assert.False(token.IsActiveAt(harness.Clock.UtcNow)));

        // Including the tablet, which never leaked anything.
        await Assert.ThrowsAsync<AuthenticationFailedException>(() => harness.Refresh.ExecuteAsync(
            new RefreshTokenCommand(second.RefreshToken, null),
            default));
    }

    [Fact]
    public async Task Refuses_an_expired_token()
    {
        var harness = new AuthHarness();
        var registered = await harness.RegisterAsync();

        harness.Clock.Advance(TimeSpan.FromDays(31));

        await Assert.ThrowsAsync<AuthenticationFailedException>(() => harness.Refresh.ExecuteAsync(
            new RefreshTokenCommand(registered.RefreshToken, null),
            default));
    }

    [Fact]
    public async Task Refuses_a_token_it_has_never_seen()
    {
        var harness = new AuthHarness();
        await harness.RegisterAsync();

        await Assert.ThrowsAsync<AuthenticationFailedException>(() => harness.Refresh.ExecuteAsync(
            new RefreshTokenCommand("made-up", null),
            default));
    }
}

public sealed class LogoutUseCaseTests
{
    [Fact]
    public async Task Revokes_the_token_the_device_hands_back()
    {
        var harness = new AuthHarness();
        var registered = await harness.RegisterAsync();

        await harness.Logout.ExecuteAsync(new LogoutCommand(registered.RefreshToken), default);

        Assert.True(harness.RefreshTokens.All[0].IsRevoked);

        await Assert.ThrowsAsync<AuthenticationFailedException>(() => harness.Refresh.ExecuteAsync(
            new RefreshTokenCommand(registered.RefreshToken, null),
            default));
    }

    [Fact]
    public async Task Succeeds_for_a_token_that_does_not_exist()
    {
        // Otherwise logout becomes a way to probe for live tokens.
        var harness = new AuthHarness();

        await harness.Logout.ExecuteAsync(new LogoutCommand("made-up"), default);
    }

    [Fact]
    public async Task Logout_everywhere_ends_every_session()
    {
        var harness = new AuthHarness();
        var first = await harness.RegisterAsync();
        await harness.Login.ExecuteAsync(
            new LoginUserCommand("lifter@example.com", "correct horse battery", "tablet"),
            default);

        harness.CurrentUser.UserId = first.UserId;

        await harness.LogoutAll.ExecuteAsync(default);

        Assert.All(harness.RefreshTokens.All, token => Assert.True(token.IsRevoked));
        Assert.All(
            harness.RefreshTokens.All,
            token => Assert.Equal(
                RefreshTokenRevocationReason.LoggedOutEverywhere,
                token.RevocationReason));
    }

    [Fact]
    public async Task Logout_everywhere_needs_somebody_to_be_signed_in()
    {
        var harness = new AuthHarness();

        await Assert.ThrowsAsync<UnauthenticatedException>(() => harness.LogoutAll.ExecuteAsync(default));
    }
}
