using GriffGym.Domain.Common;
using GriffGym.Domain.Training;
using GriffGym.Domain.Workouts;

namespace GriffGym.Domain.Tests;

public sealed class WorkoutSessionTests
{
    private static readonly DateTimeOffset Started = TrainingFixtures.Now;

    private static WorkoutSession StartedSession(out TrainingCycle cycle, out WorkoutTemplate template)
    {
        cycle = TrainingFixtures.Cycle();
        template = cycle.Program.Workouts.First();

        return WorkoutSession.StartFromTemplate(
            Guid.NewGuid(),
            cycle.UserId,
            cycle,
            template,
            DateOnly.FromDateTime(Started.UtcDateTime),
            Started,
            Started,
            Guid.NewGuid,
            Guid.NewGuid);
    }

    [Fact]
    public void Snapshots_the_plan_when_it_starts()
    {
        var session = StartedSession(out _, out var template);

        var planned = template.Exercises.Single().PlannedSets;
        var logged = session.Exercises.Single().Sets;

        Assert.Equal(planned.Count, logged.Count);
        Assert.Equal(planned[0].Weight, logged[0].PlannedWeight);
        Assert.Equal(planned[0].Reps, logged[0].PlannedReps);
        Assert.Equal(planned[0].TargetRpe, logged[0].PlannedRpe);

        // Nothing has been lifted yet.
        Assert.All(logged, set => Assert.Null(set.ActualWeight));
        Assert.All(logged, set => Assert.False(set.Completed));
    }

    [Fact]
    public void Copies_week_day_title_and_deload_rather_than_pointing_at_the_template()
    {
        var session = StartedSession(out var cycle, out var template);
        var week = cycle.Program.FindWeekOf(template.Id)!;

        Assert.Equal(week.WeekNumber, session.WeekNumber);
        Assert.Equal(template.DayNumber, session.DayNumber);
        Assert.Equal(template.Title, session.Title);
        Assert.Equal(week.IsDeload, session.IsDeload);
    }

    [Fact]
    public void Logging_a_set_never_touches_what_was_planned()
    {
        // The rule the whole history model rests on: a session shows what was asked for next to
        // what happened, and one can never overwrite the other.
        var session = StartedSession(out _, out _);
        var set = session.Exercises.Single().Sets.First();
        var plannedWeight = set.PlannedWeight;
        var plannedReps = set.PlannedReps;

        session.LogSet(
            set.Id,
            new SetResult(Weight.Of(190m), 2, Rpe.Of(9m), Completed: true, "grinder"),
            Started.AddMinutes(3));

        Assert.Equal(plannedWeight, set.PlannedWeight);
        Assert.Equal(plannedReps, set.PlannedReps);
        Assert.Equal(Weight.Of(190m), set.ActualWeight);
        Assert.Equal(2, set.ActualReps);
        Assert.Equal(Rpe.Of(9m), set.ActualRpe);
        Assert.Equal("grinder", set.Notes);
    }

    [Fact]
    public void Refuses_a_completed_set_with_nothing_recorded()
    {
        var session = StartedSession(out _, out _);
        var set = session.Exercises.Single().Sets.First();

        var exception = Assert.Throws<DomainException>(() => session.LogSet(
            set.Id,
            new SetResult(null, null, null, Completed: true, null),
            Started));

        Assert.Contains("weight and a rep count", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Refuses_a_set_that_belongs_to_another_session()
    {
        var session = StartedSession(out _, out _);

        Assert.Throws<DomainException>(() => session.LogSet(
            Guid.NewGuid(),
            new SetResult(Weight.Of(100m), 5, null, Completed: true, null),
            Started));
    }

    [Fact]
    public void Counts_volume_only_from_completed_sets()
    {
        var session = StartedSession(out _, out _);
        var sets = session.Exercises.Single().Sets;

        session.LogSet(
            sets[0].Id,
            new SetResult(Weight.Of(190m), 3, Rpe.Of(8m), Completed: true, null),
            Started);

        // Entered but not ticked off: it does not count.
        session.LogSet(
            sets[1].Id,
            new SetResult(Weight.Of(175m), 3, null, Completed: false, null),
            Started);

        Assert.Equal(570m, session.TotalVolume.Kilograms);
        Assert.Equal(1, session.CompletedSets);
        Assert.Equal(2, session.TotalSets);
        Assert.Equal(3, session.TotalReps);
    }

    [Fact]
    public void Freezes_its_volume_and_duration_on_completion()
    {
        var session = StartedSession(out _, out _);
        var set = session.Exercises.Single().Sets.First();

        session.LogSet(
            set.Id,
            new SetResult(Weight.Of(190m), 3, Rpe.Of(8m), Completed: true, null),
            Started);

        session.Complete(Started.AddMinutes(75), Started.AddMinutes(75));

        Assert.Equal(WorkoutSessionStatus.Completed, session.Status);
        Assert.Equal(TimeSpan.FromMinutes(75), session.Duration);
        Assert.Equal(570m, session.TotalVolume.Kilograms);
        Assert.True(session.IsReadOnly);
    }

    [Fact]
    public void A_finished_session_is_history_and_stops_accepting_writes()
    {
        var session = StartedSession(out _, out _);
        var set = session.Exercises.Single().Sets.First();
        session.Complete(Started.AddMinutes(60), Started.AddMinutes(60));

        Assert.Throws<DomainException>(() => session.LogSet(
            set.Id,
            new SetResult(Weight.Of(100m), 5, null, Completed: true, null),
            Started.AddMinutes(61)));

        Assert.Throws<DomainException>(() => session.UpdateNotes("late edit", Started.AddMinutes(61)));
        Assert.Throws<DomainException>(() =>
            session.Complete(Started.AddMinutes(62), Started.AddMinutes(62)));
    }

    [Fact]
    public void Cancelling_finishes_it_without_pretending_it_was_completed()
    {
        var session = StartedSession(out _, out _);

        session.Cancel(Started.AddMinutes(10), Started.AddMinutes(10));

        Assert.Equal(WorkoutSessionStatus.Cancelled, session.Status);
        Assert.True(session.IsReadOnly);
        Assert.NotNull(session.FinishedAtUtc);
    }

    [Fact]
    public void Cannot_finish_before_it_started()
    {
        var session = StartedSession(out _, out _);

        Assert.Throws<DomainException>(() =>
            session.Complete(Started.AddMinutes(-1), Started));
    }

    [Fact]
    public void Reports_the_best_estimate_it_produced_for_a_lift()
    {
        var session = StartedSession(out _, out _);
        var sets = session.Exercises.Single().Sets;

        session.LogSet(
            sets[0].Id,
            new SetResult(Weight.Of(180m), 5, Rpe.Of(8m), Completed: true, null),
            Started);
        session.LogSet(
            sets[1].Id,
            new SetResult(Weight.Of(200m), 1, Rpe.Of(9m), Completed: true, null),
            Started);

        // 180 x (1 + 5/30) = 210 beats the 200 kg single.
        Assert.Equal(Weight.Of(210m), session.BestEstimatedOneRepMax(ExerciseCategory.Squat));
        Assert.Null(session.BestEstimatedOneRepMax(ExerciseCategory.BenchPress));
    }

    [Fact]
    public void Can_be_created_already_finished_for_a_history_upload()
    {
        // A lifter who trained locally for six months uploads real history the moment they
        // create an account. Refusing anything but IN_PROGRESS would make that impossible.
        var session = WorkoutSession.Create(
            Guid.NewGuid(),
            Guid.NewGuid(),
            null,
            null,
            null,
            weekNumber: 2,
            dayNumber: 3,
            "Bench Focus / Squat Volume",
            isDeload: false,
            WorkoutSessionStatus.Completed,
            new DateOnly(2025, 11, 4),
            Started,
            Started.AddMinutes(80),
            "felt strong",
            [
                ExerciseLog.Create(
                    Guid.NewGuid(),
                    1,
                    TrainingFixtures.BenchId,
                    "Ławka",
                    ExerciseCategory.BenchPress,
                    ExerciseType.Top,
                    null,
                    [
                        SetLog.Create(
                            Guid.NewGuid(),
                            1,
                            Weight.Of(150m),
                            3,
                            RpeTarget.Exact(8m),
                            new SetResult(Weight.Of(150m), 3, Rpe.Of(8m), Completed: true, null)),
                    ]),
            ],
            Started);

        Assert.Equal(WorkoutSessionStatus.Completed, session.Status);
        Assert.Equal(450m, session.TotalVolume.Kilograms);
        Assert.Equal(TimeSpan.FromMinutes(80), session.Duration);
    }

    [Fact]
    public void A_session_that_is_finished_must_have_a_finish_time()
    {
        Assert.Throws<DomainException>(() => WorkoutSession.Create(
            Guid.NewGuid(),
            Guid.NewGuid(),
            null,
            null,
            null,
            1,
            1,
            "Workout",
            false,
            WorkoutSessionStatus.InProgress,
            new DateOnly(2026, 3, 2),
            Started,
            // In progress, yet finished at a time: an impossible row.
            Started.AddMinutes(30),
            null,
            [],
            Started));
    }
}
