using GriffGym.Domain.Training;
using GriffGym.Domain.Workouts;
using GriffGym.Infrastructure.Persistence.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace GriffGym.Infrastructure.Persistence.Configurations;

internal sealed class WorkoutSessionConfiguration : IEntityTypeConfiguration<WorkoutSessionRecord>
{
    public void Configure(EntityTypeBuilder<WorkoutSessionRecord> builder)
    {
        builder.ToTable("workout_session");
        builder.HasKey(session => session.Id);
        builder.Property(session => session.Id).ValueGeneratedNever();

        builder.Property(session => session.UserId).IsRequired();
        builder.Property(session => session.WeekNumber).IsRequired();
        builder.Property(session => session.DayNumber).IsRequired();
        builder.Property(session => session.Title)
            .IsRequired()
            .HasMaxLength(WorkoutSession.MaxTitleLength);
        builder.Property(session => session.IsDeload).IsRequired();
        builder.Property(session => session.Status)
            .IsRequired()
            .HasConversion<string>()
            .HasMaxLength(32);
        builder.Property(session => session.PerformedOn).IsRequired();
        builder.Property(session => session.StartedAtUtc).IsRequired();
        builder.Property(session => session.FinishedAtUtc);
        builder.Property(session => session.TotalVolumeKg).HasColumnType(ColumnTypes.Volume);
        builder.Property(session => session.Notes).HasMaxLength(WorkoutSession.MaxNotesLength);

        builder.ConfigureSyncMetadata();

        builder.HasOne<UserRecord>()
            .WithMany()
            .HasForeignKey(session => session.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // History outlives the plan it came from. Deleting a cycle must never take the record of
        // what was actually trained with it, so these links are severed rather than cascaded.
        builder.HasOne<TrainingCycleRecord>()
            .WithMany()
            .HasForeignKey(session => session.TrainingCycleId)
            .OnDelete(DeleteBehavior.SetNull);

        builder.HasOne<TrainingWeekRecord>()
            .WithMany()
            .HasForeignKey(session => session.TrainingWeekId)
            .OnDelete(DeleteBehavior.SetNull);

        builder.HasOne<WorkoutTemplateRecord>()
            .WithMany()
            .HasForeignKey(session => session.WorkoutTemplateId)
            .OnDelete(DeleteBehavior.SetNull);

        // Newest-first history, and the "which workout am I in?" lookup.
        builder.HasIndex(session => new { session.UserId, session.PerformedOn });
        builder.HasIndex(session => new { session.UserId, session.Status });
        builder.HasIndex(session => new { session.UserId, session.TrainingCycleId, session.WeekNumber });

        builder.HasMany(session => session.Exercises)
            .WithOne(exercise => exercise.Session)
            .HasForeignKey(exercise => exercise.WorkoutSessionId)
            .OnDelete(DeleteBehavior.Cascade);
    }
}

internal sealed class ExerciseLogConfiguration : IEntityTypeConfiguration<ExerciseLogRecord>
{
    public void Configure(EntityTypeBuilder<ExerciseLogRecord> builder)
    {
        builder.ToTable("exercise_log");
        builder.HasKey(exercise => exercise.Id);
        builder.Property(exercise => exercise.Id).ValueGeneratedNever();

        builder.Property(exercise => exercise.ExerciseName)
            .IsRequired()
            .HasMaxLength(Exercise.MaxNameLength);
        builder.Property(exercise => exercise.ExerciseCategory)
            .IsRequired()
            .HasConversion<string>()
            .HasMaxLength(32);
        builder.Property(exercise => exercise.Type)
            .IsRequired()
            .HasConversion<string>()
            .HasMaxLength(32);
        builder.Property(exercise => exercise.Position).IsRequired();
        builder.Property(exercise => exercise.Notes).HasMaxLength(ExerciseLog.MaxNotesLength);

        builder.HasIndex(exercise => new { exercise.WorkoutSessionId, exercise.Position }).IsUnique();

        // The name is snapshotted on the row, so losing the catalogue entry loses nothing.
        builder.HasOne<ExerciseRecord>()
            .WithMany()
            .HasForeignKey(exercise => exercise.ExerciseId)
            .OnDelete(DeleteBehavior.SetNull);

        builder.HasMany(exercise => exercise.Sets)
            .WithOne(set => set.ExerciseLog)
            .HasForeignKey(set => set.ExerciseLogId)
            .OnDelete(DeleteBehavior.Cascade);
    }
}

internal sealed class SetLogConfiguration : IEntityTypeConfiguration<SetLogRecord>
{
    public void Configure(EntityTypeBuilder<SetLogRecord> builder)
    {
        builder.ToTable("set_log");
        builder.HasKey(set => set.Id);
        builder.Property(set => set.Id).ValueGeneratedNever();

        builder.Property(set => set.Position).IsRequired();

        builder.Property(set => set.PlannedWeightKg).HasColumnType(ColumnTypes.Weight);
        builder.Property(set => set.PlannedReps);
        builder.Property(set => set.PlannedRpeMin).HasColumnType(ColumnTypes.Rpe);
        builder.Property(set => set.PlannedRpeMax).HasColumnType(ColumnTypes.Rpe);

        builder.Property(set => set.ActualWeightKg).HasColumnType(ColumnTypes.Weight);
        builder.Property(set => set.ActualReps);
        builder.Property(set => set.ActualRpe).HasColumnType(ColumnTypes.Rpe);

        builder.Property(set => set.Completed).IsRequired();
        builder.Property(set => set.Notes).HasMaxLength(SetLog.MaxNotesLength);

        builder.HasIndex(set => new { set.ExerciseLogId, set.Position }).IsUnique();
    }
}
