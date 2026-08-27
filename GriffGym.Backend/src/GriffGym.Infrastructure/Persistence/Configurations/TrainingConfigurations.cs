using GriffGym.Domain.Training;
using GriffGym.Infrastructure.Persistence.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace GriffGym.Infrastructure.Persistence.Configurations;

/// <summary>
/// Every weight and RPE column in the schema, in one place.
///
/// Kilograms are <c>numeric(7,2)</c> and never a floating point type: 117.5, 132.5 and 162.5
/// are ordinary loads on this program, and a double that reads back as 117.49999999999999 is a
/// corrupted training log. RPE is <c>numeric(3,1)</c> — 1.0 to 10.0 in half steps.
/// </summary>
internal static class ColumnTypes
{
    public const string Weight = "numeric(7,2)";
    public const string Rpe = "numeric(3,1)";

    /// <summary>Tonnage across a whole session runs to tens of thousands of kilograms.</summary>
    public const string Volume = "numeric(12,2)";
}

internal sealed class ExerciseConfiguration : IEntityTypeConfiguration<ExerciseRecord>
{
    public void Configure(EntityTypeBuilder<ExerciseRecord> builder)
    {
        builder.ToTable("exercise");
        builder.HasKey(exercise => exercise.Id);
        builder.Property(exercise => exercise.Id).ValueGeneratedNever();

        builder.Property(exercise => exercise.UserId).IsRequired();
        builder.Property(exercise => exercise.Name)
            .IsRequired()
            .HasMaxLength(Exercise.MaxNameLength);
        builder.Property(exercise => exercise.Category)
            .IsRequired()
            .HasConversion<string>()
            .HasMaxLength(32);

        builder.ConfigureSyncMetadata();

        builder.HasOne<UserRecord>()
            .WithMany()
            .HasForeignKey(exercise => exercise.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // The catalogue is per lifter, so two people may both own a "Przysiad".
        builder.HasIndex(exercise => new { exercise.UserId, exercise.Name }).IsUnique();
    }
}

internal sealed class ReferenceMaxConfiguration : IEntityTypeConfiguration<ReferenceMaxRecord>
{
    public void Configure(EntityTypeBuilder<ReferenceMaxRecord> builder)
    {
        builder.ToTable("reference_max");
        builder.HasKey(max => max.Id);
        builder.Property(max => max.Id).ValueGeneratedNever();

        builder.Property(max => max.UserId).IsRequired();
        builder.Property(max => max.Lift).IsRequired().HasConversion<string>().HasMaxLength(32);
        builder.Property(max => max.ValueKg).IsRequired().HasColumnType(ColumnTypes.Weight);

        builder.ConfigureSyncMetadata();

        builder.HasOne<UserRecord>()
            .WithMany()
            .HasForeignKey(max => max.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // Exactly one squat max per lifter. Enforced by the database, not by hoping.
        builder.HasIndex(max => new { max.UserId, max.Lift }).IsUnique();
    }
}

internal sealed class TrainingCycleConfiguration : IEntityTypeConfiguration<TrainingCycleRecord>
{
    public void Configure(EntityTypeBuilder<TrainingCycleRecord> builder)
    {
        builder.ToTable("training_cycle");
        builder.HasKey(cycle => cycle.Id);
        builder.Property(cycle => cycle.Id).ValueGeneratedNever();

        builder.Property(cycle => cycle.UserId).IsRequired();
        builder.Property(cycle => cycle.CycleNumber).IsRequired();
        builder.Property(cycle => cycle.Status).IsRequired().HasConversion<string>().HasMaxLength(32);

        builder.Property(cycle => cycle.SquatReferenceMaxKg)
            .IsRequired()
            .HasColumnType(ColumnTypes.Weight);
        builder.Property(cycle => cycle.BenchPressReferenceMaxKg)
            .IsRequired()
            .HasColumnType(ColumnTypes.Weight);
        builder.Property(cycle => cycle.DeadliftReferenceMaxKg)
            .IsRequired()
            .HasColumnType(ColumnTypes.Weight);

        builder.Property(cycle => cycle.StartedAtUtc).IsRequired();
        builder.Property(cycle => cycle.CompletedAtUtc);

        builder.ConfigureSyncMetadata();

        builder.HasOne<UserRecord>()
            .WithMany()
            .HasForeignKey(cycle => cycle.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // Cycles are numbered from one per lifter and never renumbered.
        builder.HasIndex(cycle => new { cycle.UserId, cycle.CycleNumber }).IsUnique();

        // "Which cycle am I in?" is the highest number for this user.
        builder.HasIndex(cycle => new { cycle.UserId, cycle.Status });

        builder.HasOne(cycle => cycle.Program)
            .WithOne(program => program.Cycle)
            .HasForeignKey<TrainingProgramRecord>(program => program.TrainingCycleId)
            .OnDelete(DeleteBehavior.Cascade);
    }
}

internal sealed class TrainingProgramConfiguration : IEntityTypeConfiguration<TrainingProgramRecord>
{
    public void Configure(EntityTypeBuilder<TrainingProgramRecord> builder)
    {
        builder.ToTable("training_program");
        builder.HasKey(program => program.Id);
        builder.Property(program => program.Id).ValueGeneratedNever();

        builder.Property(program => program.Name)
            .IsRequired()
            .HasMaxLength(TrainingProgram.MaxNameLength);
        builder.Property(program => program.CurrentWorkoutTemplateId);

        // A cycle owns exactly one program.
        builder.HasIndex(program => program.TrainingCycleId).IsUnique();

        builder.HasMany(program => program.Weeks)
            .WithOne(week => week.Program)
            .HasForeignKey(week => week.TrainingProgramId)
            .OnDelete(DeleteBehavior.Cascade);

        // Deliberately no foreign key on the progress pointer: it addresses a row inside this
        // same tree, and a self-referencing constraint would only complicate inserting the tree
        // in one statement. The domain validates that the pointer names a workout it owns.
    }
}

internal sealed class TrainingWeekConfiguration : IEntityTypeConfiguration<TrainingWeekRecord>
{
    public void Configure(EntityTypeBuilder<TrainingWeekRecord> builder)
    {
        builder.ToTable("training_week");
        builder.HasKey(week => week.Id);
        builder.Property(week => week.Id).ValueGeneratedNever();

        builder.Property(week => week.WeekNumber).IsRequired();
        builder.Property(week => week.Label).IsRequired().HasMaxLength(64);
        builder.Property(week => week.Type).IsRequired().HasConversion<string>().HasMaxLength(32);

        builder.HasIndex(week => new { week.TrainingProgramId, week.WeekNumber }).IsUnique();

        builder.HasMany(week => week.Workouts)
            .WithOne(workout => workout.Week)
            .HasForeignKey(workout => workout.TrainingWeekId)
            .OnDelete(DeleteBehavior.Cascade);
    }
}

internal sealed class WorkoutTemplateConfiguration : IEntityTypeConfiguration<WorkoutTemplateRecord>
{
    public void Configure(EntityTypeBuilder<WorkoutTemplateRecord> builder)
    {
        builder.ToTable("workout_template");
        builder.HasKey(workout => workout.Id);
        builder.Property(workout => workout.Id).ValueGeneratedNever();

        builder.Property(workout => workout.DayNumber).IsRequired();
        builder.Property(workout => workout.SequenceNumber).IsRequired();
        builder.Property(workout => workout.Title)
            .IsRequired()
            .HasMaxLength(GriffGym.Domain.Workouts.WorkoutSession.MaxTitleLength);

        builder.HasIndex(workout => new { workout.TrainingWeekId, workout.DayNumber }).IsUnique();

        builder.HasMany(workout => workout.Exercises)
            .WithOne(exercise => exercise.Workout)
            .HasForeignKey(exercise => exercise.WorkoutTemplateId)
            .OnDelete(DeleteBehavior.Cascade);
    }
}

internal sealed class ExerciseTemplateConfiguration : IEntityTypeConfiguration<ExerciseTemplateRecord>
{
    public void Configure(EntityTypeBuilder<ExerciseTemplateRecord> builder)
    {
        builder.ToTable("exercise_template");
        builder.HasKey(exercise => exercise.Id);
        builder.Property(exercise => exercise.Id).ValueGeneratedNever();

        builder.Property(exercise => exercise.ExerciseId).IsRequired();
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

        builder.HasIndex(exercise => new { exercise.WorkoutTemplateId, exercise.Position }).IsUnique();

        // Restrict, not cascade: a plan that prescribes a movement is a reason not to delete it.
        builder.HasOne<ExerciseRecord>()
            .WithMany()
            .HasForeignKey(exercise => exercise.ExerciseId)
            .OnDelete(DeleteBehavior.Restrict);

        builder.HasMany(exercise => exercise.PlannedSets)
            .WithOne(set => set.ExerciseTemplate)
            .HasForeignKey(set => set.ExerciseTemplateId)
            .OnDelete(DeleteBehavior.Cascade);
    }
}

internal sealed class PlannedSetConfiguration : IEntityTypeConfiguration<PlannedSetRecord>
{
    public void Configure(EntityTypeBuilder<PlannedSetRecord> builder)
    {
        builder.ToTable("planned_set");
        builder.HasKey(set => set.Id);
        builder.Property(set => set.Id).ValueGeneratedNever();

        builder.Property(set => set.Position).IsRequired();
        builder.Property(set => set.WeightKg).HasColumnType(ColumnTypes.Weight);
        builder.Property(set => set.Reps);
        builder.Property(set => set.RpeMin).HasColumnType(ColumnTypes.Rpe);
        builder.Property(set => set.RpeMax).HasColumnType(ColumnTypes.Rpe);

        builder.HasIndex(set => new { set.ExerciseTemplateId, set.Position }).IsUnique();
    }
}
