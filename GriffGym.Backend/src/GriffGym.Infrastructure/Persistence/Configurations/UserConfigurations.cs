using GriffGym.Domain.Users;
using GriffGym.Infrastructure.Persistence.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace GriffGym.Infrastructure.Persistence.Configurations;

internal sealed class UserConfiguration : IEntityTypeConfiguration<UserRecord>
{
    public void Configure(EntityTypeBuilder<UserRecord> builder)
    {
        builder.ToTable("user");
        builder.HasKey(user => user.Id);
        builder.Property(user => user.Id).ValueGeneratedNever();

        builder.Property(user => user.Email).IsRequired().HasMaxLength(EmailAddress.MaxLength);
        builder.Property(user => user.NormalizedEmail)
            .IsRequired()
            .HasMaxLength(EmailAddress.MaxLength);

        // One account per address, whatever the lifter capitalised.
        builder.HasIndex(user => user.NormalizedEmail).IsUnique();

        builder.Property(user => user.PasswordHash).IsRequired().HasMaxLength(512);
        builder.Property(user => user.SecurityStamp).IsRequired().HasMaxLength(64);

        builder.ConfigureSyncMetadata();

        builder.HasMany(user => user.RefreshTokens)
            .WithOne(token => token.User)
            .HasForeignKey(token => token.UserId)
            .OnDelete(DeleteBehavior.Cascade);
    }
}

internal sealed class RefreshTokenConfiguration : IEntityTypeConfiguration<RefreshTokenRecord>
{
    public void Configure(EntityTypeBuilder<RefreshTokenRecord> builder)
    {
        builder.ToTable("refresh_token");
        builder.HasKey(token => token.Id);
        builder.Property(token => token.Id).ValueGeneratedNever();

        // Base64 of a SHA-256 digest. Fixed width, and never the token itself.
        builder.Property(token => token.TokenHash).IsRequired().HasMaxLength(128);

        // Lookup on refresh is by hash and nothing else, and two live tokens must never share
        // one — a collision here would let one device's token unlock another's session.
        builder.HasIndex(token => token.TokenHash).IsUnique();

        builder.Property(token => token.DeviceId).HasMaxLength(128);
        builder.Property(token => token.ExpiresAtUtc).IsRequired();
        builder.Property(token => token.RevokedAtUtc);
        builder.Property(token => token.RevocationReason)
            .IsRequired()
            .HasConversion<string>()
            .HasMaxLength(32);
        builder.Property(token => token.ReplacedByTokenId);
        builder.Property(token => token.CreatedAtUtc).IsRequired();
        builder.Property(token => token.UpdatedAtUtc).IsRequired();

        // "Revoke everything for this user" and "list this lifter's sessions" both start here.
        builder.HasIndex(token => new { token.UserId, token.ExpiresAtUtc });
    }
}
