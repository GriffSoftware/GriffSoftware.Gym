using GriffGym.Infrastructure.Persistence.Entities;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace GriffGym.Infrastructure.Persistence.Configurations;

internal static class SyncableConfiguration
{
    /// <summary>
    /// The columns and indexes every synchronised table needs, applied in one place so a new
    /// table cannot quietly be born without a concurrency token or a delta-sync cursor.
    /// </summary>
    public static void ConfigureSyncMetadata<T>(this EntityTypeBuilder<T> builder)
        where T : class, ISyncable
    {
        // The token EF matches on when updating. A phone writing over a revision another device
        // already replaced gets a concurrency failure, not a silent overwrite.
        builder.Property(entity => entity.Version).IsConcurrencyToken().IsRequired();

        builder.Property(entity => entity.SyncVersion).IsRequired();
        builder.Property(entity => entity.CreatedAtUtc).IsRequired();
        builder.Property(entity => entity.UpdatedAtUtc).IsRequired();
        builder.Property(entity => entity.DeletedAtUtc);

        // The index a future "everything above cursor N" query pages through.
        builder.HasIndex(entity => entity.SyncVersion);
    }
}
