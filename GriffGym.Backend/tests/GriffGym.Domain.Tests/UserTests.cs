using GriffGym.Domain.Common;
using GriffGym.Domain.Users;

namespace GriffGym.Domain.Tests;

public sealed class EmailAddressTests
{
    [Fact]
    public void Normalises_to_upper_invariant()
    {
        // "Pawel@Example.com" and "pawel@example.com" are one account, and the unique index is
        // on the normalised form.
        var email = EmailAddress.Of("  Pawel@Example.com ");

        Assert.Equal("Pawel@Example.com", email.Value);
        Assert.Equal("PAWEL@EXAMPLE.COM", email.Normalized);
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("no-at-sign")]
    [InlineData("two@at@signs.com")]
    [InlineData("trailing@")]
    [InlineData("@leading.com")]
    [InlineData("spaces in@example.com")]
    [InlineData("no-dot@localhost")]
    public void Rejects_an_implausible_address(string value) =>
        Assert.Throws<DomainException>(() => EmailAddress.Of(value));

    [Theory]
    [InlineData("contact@griffsoftware.com")]
    [InlineData("first.last+tag@sub.example.co.uk")]
    public void Accepts_an_ordinary_address(string value) =>
        Assert.Equal(value, EmailAddress.Of(value).Value);
}

public sealed class UserTests
{
    private static readonly DateTimeOffset Now = new(2026, 1, 5, 9, 0, 0, TimeSpan.Zero);

    [Fact]
    public void Registers_with_a_hash_and_a_security_stamp()
    {
        var user = User.Register(
            Guid.NewGuid(),
            EmailAddress.Of("lifter@example.com"),
            "hashed",
            Now);

        Assert.Equal("hashed", user.PasswordHash);
        Assert.NotEmpty(user.SecurityStamp);
        Assert.Equal(Now, user.CreatedAtUtc);
    }

    [Fact]
    public void Refuses_to_exist_without_a_password_hash()
    {
        Assert.Throws<DomainException>(() =>
            User.Register(Guid.NewGuid(), EmailAddress.Of("lifter@example.com"), "  ", Now));
    }

    [Fact]
    public void Changing_a_password_rolls_the_security_stamp()
    {
        // The stamp is a claim on every access token, so rolling it is what lets a future
        // password change invalidate tokens already in the wild.
        var user = User.Register(Guid.NewGuid(), EmailAddress.Of("l@example.com"), "old", Now);
        var before = user.SecurityStamp;

        user.ChangePassword("new", Now.AddDays(1));

        Assert.NotEqual(before, user.SecurityStamp);
        Assert.Equal(Now.AddDays(1), user.UpdatedAtUtc);
    }

    [Fact]
    public void Re_hashing_after_a_work_factor_bump_is_not_a_credential_change()
    {
        var user = User.Register(Guid.NewGuid(), EmailAddress.Of("l@example.com"), "old", Now);
        var before = user.SecurityStamp;

        user.UpgradePasswordHash("stronger-hash-same-password", Now.AddDays(1));

        Assert.Equal(before, user.SecurityStamp);
        Assert.Equal("stronger-hash-same-password", user.PasswordHash);
    }
}

public sealed class RefreshTokenTests
{
    private static readonly DateTimeOffset Now = new(2026, 1, 5, 9, 0, 0, TimeSpan.Zero);
    private static readonly TimeSpan Month = TimeSpan.FromDays(30);

    private static RefreshToken Issue(Guid userId, string hash = "hash-a") =>
        RefreshToken.Issue(Guid.NewGuid(), userId, hash, "pixel-9", Now, Month);

    [Fact]
    public void Is_active_until_it_expires()
    {
        var token = Issue(Guid.NewGuid());

        Assert.True(token.IsActiveAt(Now.AddDays(29)));
        Assert.False(token.IsActiveAt(Now.Add(Month)));
    }

    [Fact]
    public void Stores_only_a_hash()
    {
        var token = Issue(Guid.NewGuid(), "sha256-digest");

        Assert.Equal("sha256-digest", token.TokenHash);
        Assert.Null(typeof(RefreshToken).GetProperty("Value"));
    }

    [Fact]
    public void Rotating_retires_it_and_records_the_replacement()
    {
        var userId = Guid.NewGuid();
        var original = Issue(userId, "hash-a");
        var replacement = Issue(userId, "hash-b");

        original.RotateTo(replacement, Now.AddMinutes(20));

        Assert.True(original.IsRevoked);
        Assert.True(original.WasRotated);
        Assert.Equal(replacement.Id, original.ReplacedByTokenId);
        Assert.Equal(RefreshTokenRevocationReason.Rotated, original.RevocationReason);
        Assert.False(original.IsActiveAt(Now.AddMinutes(21)));
    }

    [Fact]
    public void A_rotated_token_is_the_signal_that_something_leaked()
    {
        // The legitimate holder moved on to the replacement, so anybody presenting the old one
        // is a second party holding the same secret.
        var userId = Guid.NewGuid();
        var original = Issue(userId, "hash-a");
        original.RotateTo(Issue(userId, "hash-b"), Now.AddMinutes(20));

        Assert.True(original.WasRotated);
    }

    [Fact]
    public void Cannot_be_replaced_by_another_users_token()
    {
        var original = Issue(Guid.NewGuid());
        var foreign = Issue(Guid.NewGuid());

        Assert.Throws<DomainException>(() => original.RotateTo(foreign, Now));
    }

    [Fact]
    public void Cannot_be_rotated_once_revoked()
    {
        var userId = Guid.NewGuid();
        var token = Issue(userId);
        token.Revoke(Now.AddMinutes(5), RefreshTokenRevocationReason.LoggedOut);

        Assert.Throws<DomainException>(() =>
            token.RotateTo(Issue(userId, "hash-b"), Now.AddMinutes(6)));
    }

    [Fact]
    public void Revoking_twice_keeps_the_first_reason()
    {
        var token = Issue(Guid.NewGuid());
        token.Revoke(Now.AddMinutes(5), RefreshTokenRevocationReason.LoggedOut);
        token.Revoke(Now.AddMinutes(9), RefreshTokenRevocationReason.ReuseDetected);

        Assert.Equal(RefreshTokenRevocationReason.LoggedOut, token.RevocationReason);
        Assert.Equal(Now.AddMinutes(5), token.RevokedAtUtc);
    }

    [Fact]
    public void Must_expire_after_it_was_created()
    {
        Assert.Throws<DomainException>(() =>
            RefreshToken.Issue(Guid.NewGuid(), Guid.NewGuid(), "hash", null, Now, TimeSpan.Zero));
    }
}
