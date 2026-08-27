using System.Text;
using GriffGym.Infrastructure.Persistence.Configurations;
using GriffGym.Infrastructure.Persistence.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage.ValueConversion;

namespace GriffGym.Infrastructure.Persistence;

public sealed class GriffGymDbContext(DbContextOptions<GriffGymDbContext> options) : DbContext(options)
{
    /// <summary>
    /// One sequence for the whole database, handing out the ordering a future delta sync pages
    /// through. Not per table: "what changed since 4 812?" has to have a single answer across
    /// cycles, workouts and reference maxes at once.
    /// </summary>
    public const string SyncVersionSequence = "griffgym_sync_version";

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.HasSequence<long>(SyncVersionSequence).StartsAt(1).IncrementsBy(1);

        modelBuilder.ApplyConfiguration(new UserConfiguration());
        modelBuilder.ApplyConfiguration(new RefreshTokenConfiguration());
        modelBuilder.ApplyConfiguration(new ExerciseConfiguration());
        modelBuilder.ApplyConfiguration(new ReferenceMaxConfiguration());
        modelBuilder.ApplyConfiguration(new TrainingCycleConfiguration());
        modelBuilder.ApplyConfiguration(new TrainingProgramConfiguration());
        modelBuilder.ApplyConfiguration(new TrainingWeekConfiguration());
        modelBuilder.ApplyConfiguration(new WorkoutTemplateConfiguration());
        modelBuilder.ApplyConfiguration(new ExerciseTemplateConfiguration());
        modelBuilder.ApplyConfiguration(new PlannedSetConfiguration());
        modelBuilder.ApplyConfiguration(new WorkoutSessionConfiguration());
        modelBuilder.ApplyConfiguration(new ExerciseLogConfiguration());
        modelBuilder.ApplyConfiguration(new SetLogConfiguration());

        NormaliseTimestampsToUtc(modelBuilder);
        UseSnakeCaseNames(modelBuilder);
    }

    /// <summary>
    /// Npgsql refuses a <see cref="DateTimeOffset"/> that is not already UTC when writing to
    /// <c>timestamptz</c>. Converting here rather than at each call site means no code path can
    /// forget, and a client sending "+02:00" is stored as the instant it means.
    /// </summary>
    private static void NormaliseTimestampsToUtc(ModelBuilder modelBuilder)
    {
        var converter = new ValueConverter<DateTimeOffset, DateTimeOffset>(
            value => value.ToUniversalTime(),
            value => value.ToUniversalTime());

        var nullableConverter = new ValueConverter<DateTimeOffset?, DateTimeOffset?>(
            value => value.HasValue ? value.Value.ToUniversalTime() : null,
            value => value.HasValue ? value.Value.ToUniversalTime() : null);

        foreach (var entity in modelBuilder.Model.GetEntityTypes())
        {
            foreach (var property in entity.GetProperties())
            {
                if (property.ClrType == typeof(DateTimeOffset))
                {
                    property.SetValueConverter(converter);
                }
                else if (property.ClrType == typeof(DateTimeOffset?))
                {
                    property.SetValueConverter(nullableConverter);
                }
            }
        }
    }

    /// <summary>
    /// PostgreSQL folds unquoted identifiers to lower case, so <c>CreatedAtUtc</c> only works if
    /// every statement quotes it forever. Naming the columns <c>created_at_utc</c> up front
    /// means psql, backups and any future reporting tool see the names this code uses.
    /// </summary>
    private static void UseSnakeCaseNames(ModelBuilder modelBuilder)
    {
        foreach (var entity in modelBuilder.Model.GetEntityTypes())
        {
            var tableName = entity.GetTableName();
            if (tableName is not null)
            {
                entity.SetTableName(ToSnakeCase(tableName));
            }

            foreach (var property in entity.GetProperties())
            {
                property.SetColumnName(ToSnakeCase(property.GetColumnName()));
            }

            foreach (var key in entity.GetKeys())
            {
                key.SetName(ToSnakeCase(key.GetName() ?? string.Empty));
            }

            foreach (var foreignKey in entity.GetForeignKeys())
            {
                foreignKey.SetConstraintName(ToSnakeCase(foreignKey.GetConstraintName() ?? string.Empty));
            }

            foreach (var index in entity.GetIndexes())
            {
                index.SetDatabaseName(ToSnakeCase(index.GetDatabaseName() ?? string.Empty));
            }
        }
    }

    private static string ToSnakeCase(string name)
    {
        if (string.IsNullOrEmpty(name))
        {
            return name;
        }

        var builder = new StringBuilder(name.Length + 8);

        for (var index = 0; index < name.Length; index++)
        {
            var character = name[index];

            if (char.IsUpper(character))
            {
                var previous = index > 0 ? name[index - 1] : '\0';
                var next = index + 1 < name.Length ? name[index + 1] : '\0';

                var startsWord = index > 0
                                 && previous != '_'
                                 && (!char.IsUpper(previous) || (char.IsUpper(previous) && char.IsLower(next)));

                if (startsWord)
                {
                    builder.Append('_');
                }

                builder.Append(char.ToLowerInvariant(character));
            }
            else
            {
                builder.Append(character);
            }
        }

        return builder.ToString();
    }
}
