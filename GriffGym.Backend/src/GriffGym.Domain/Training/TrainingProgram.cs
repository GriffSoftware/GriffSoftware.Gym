using GriffGym.Domain.Common;

namespace GriffGym.Domain.Training;

/// <summary>
/// A single prescribed set inside a template.
///
/// Accessory work has no prescribed load, hence the nullable <see cref="Weight"/>. Everything
/// here is read-only: once a cycle exists, its plan is history and history does not change.
/// </summary>
public sealed class PlannedSet
{
    public PlannedSet(Guid id, int position, Weight? weight, int? reps, RpeTarget? targetRpe)
    {
        DomainException.Require(id != Guid.Empty, "A planned set needs a non-empty identifier.");
        DomainException.Require(position >= 1, $"Set positions start at one, got {position}.");
        DomainException.Require(reps is null or >= 1, $"A planned set cannot prescribe {reps} reps.");

        Id = id;
        Position = position;
        Weight = weight;
        Reps = reps;
        TargetRpe = targetRpe;
    }

    public Guid Id { get; }

    public int Position { get; }

    public Weight? Weight { get; }

    public int? Reps { get; }

    public RpeTarget? TargetRpe { get; }
}

/// <summary>
/// One movement inside a planned workout, with the sets prescribed for it.
///
/// The name and category are copied in alongside <see cref="ExerciseId"/>. The link is
/// provenance; the snapshot is the truth, so renaming a movement in the catalogue years later
/// cannot rewrite what a cycle actually prescribed.
/// </summary>
public sealed class ExerciseTemplate
{
    public ExerciseTemplate(
        Guid id,
        int position,
        Guid exerciseId,
        string exerciseName,
        ExerciseCategory exerciseCategory,
        ExerciseType type,
        IReadOnlyList<PlannedSet> plannedSets)
    {
        DomainException.Require(id != Guid.Empty, "An exercise template needs a non-empty identifier.");
        DomainException.Require(position >= 1, $"Exercise positions start at one, got {position}.");
        DomainException.Require(exerciseId != Guid.Empty, "An exercise template must reference an exercise.");
        DomainException.Require(
            !string.IsNullOrWhiteSpace(exerciseName),
            "An exercise template must snapshot the exercise name.");
        DomainException.Require(plannedSets.Count > 0, $"'{exerciseName}' prescribes no sets.");

        var ordered = plannedSets.OrderBy(set => set.Position).ToList();
        DomainException.Require(
            ordered.Select(set => set.Position).Distinct().Count() == ordered.Count,
            $"'{exerciseName}' has two sets at the same position.");

        Id = id;
        Position = position;
        ExerciseId = exerciseId;
        ExerciseName = exerciseName.Trim();
        ExerciseCategory = exerciseCategory;
        Type = type;
        PlannedSets = ordered;
    }

    public Guid Id { get; }

    public int Position { get; }

    public Guid ExerciseId { get; }

    public string ExerciseName { get; }

    public ExerciseCategory ExerciseCategory { get; }

    public ExerciseType Type { get; }

    public IReadOnlyList<PlannedSet> PlannedSets { get; }

    public bool IsMainLift => Type.IsMainLift();
}

/// <summary>One planned training day: "Week 3, Day II — Deadlift Focus / Bench Light".</summary>
public sealed class WorkoutTemplate
{
    public WorkoutTemplate(
        Guid id,
        int dayNumber,
        int sequenceNumber,
        string title,
        IReadOnlyList<ExerciseTemplate> exercises)
    {
        DomainException.Require(id != Guid.Empty, "A workout template needs a non-empty identifier.");
        DomainException.Require(dayNumber >= 1, $"Day numbers start at one, got {dayNumber}.");
        DomainException.Require(
            sequenceNumber >= 1,
            $"Sequence numbers start at one, got {sequenceNumber}.");
        DomainException.Require(!string.IsNullOrWhiteSpace(title), "A workout template needs a title.");
        DomainException.Require(exercises.Count > 0, $"'{title}' prescribes no exercises.");

        var ordered = exercises.OrderBy(exercise => exercise.Position).ToList();
        DomainException.Require(
            ordered.Select(exercise => exercise.Position).Distinct().Count() == ordered.Count,
            $"'{title}' has two exercises at the same position.");

        Id = id;
        DayNumber = dayNumber;
        SequenceNumber = sequenceNumber;
        Title = title.Trim();
        Exercises = ordered;
    }

    public Guid Id { get; }

    public int DayNumber { get; }

    /// <summary>
    /// Position of this unit in the whole program. The plan is a sequence, not a calendar:
    /// training a day early or a week late makes no difference to what comes next.
    /// </summary>
    public int SequenceNumber { get; }

    public string Title { get; }

    public IReadOnlyList<ExerciseTemplate> Exercises { get; }

    public IEnumerable<ExerciseTemplate> MainLifts => Exercises.Where(exercise => exercise.IsMainLift);
}

/// <summary>One week of the block. Weeks 1-5 train; week 6 deloads.</summary>
public sealed class TrainingWeek
{
    public TrainingWeek(
        Guid id,
        int weekNumber,
        string label,
        TrainingWeekType type,
        IReadOnlyList<WorkoutTemplate> workouts)
    {
        DomainException.Require(id != Guid.Empty, "A training week needs a non-empty identifier.");
        DomainException.Require(weekNumber >= 1, $"Week numbers start at one, got {weekNumber}.");
        DomainException.Require(!string.IsNullOrWhiteSpace(label), "A training week needs a label.");
        DomainException.Require(workouts.Count > 0, $"Week {weekNumber} contains no workouts.");

        var ordered = workouts.OrderBy(workout => workout.DayNumber).ToList();
        DomainException.Require(
            ordered.Select(workout => workout.DayNumber).Distinct().Count() == ordered.Count,
            $"Week {weekNumber} has two workouts on the same day.");

        Id = id;
        WeekNumber = weekNumber;
        Label = label.Trim();
        Type = type;
        Workouts = ordered;
    }

    public Guid Id { get; }

    public int WeekNumber { get; }

    public string Label { get; }

    public TrainingWeekType Type { get; }

    public IReadOnlyList<WorkoutTemplate> Workouts { get; }

    public bool IsDeload => Type == TrainingWeekType.Deload;
}

/// <summary>
/// The generated plan of one cycle, plus a pointer at where the lifter is inside it.
///
/// Storing the whole tree — not just "cycle 3" and a rule for regenerating it — is what makes
/// history immutable. If the template the app ships ever changes, cycle 1 keeps the plan it
/// was actually trained on.
/// </summary>
public sealed class TrainingProgram
{
    public const int MaxNameLength = 200;

    public TrainingProgram(
        Guid id,
        string name,
        IReadOnlyList<TrainingWeek> weeks,
        Guid? currentWorkoutTemplateId)
    {
        DomainException.Require(id != Guid.Empty, "A training program needs a non-empty identifier.");
        DomainException.Require(!string.IsNullOrWhiteSpace(name), "A training program needs a name.");
        DomainException.Require(
            name.Trim().Length <= MaxNameLength,
            $"A training program name must be at most {MaxNameLength} characters.");
        DomainException.Require(weeks.Count > 0, "A training program contains no weeks.");

        var ordered = weeks.OrderBy(week => week.WeekNumber).ToList();
        DomainException.Require(
            ordered.Select(week => week.WeekNumber).Distinct().Count() == ordered.Count,
            "A training program has two weeks with the same number.");

        var workouts = ordered.SelectMany(week => week.Workouts).ToList();
        DomainException.Require(
            workouts.Select(workout => workout.SequenceNumber).Distinct().Count() == workouts.Count,
            "A training program has two workouts at the same position in the sequence.");
        DomainException.Require(
            workouts.Select(workout => workout.Id).Distinct().Count() == workouts.Count,
            "A training program has two workouts with the same identifier.");

        Id = id;
        Name = name.Trim();
        Weeks = ordered;

        if (currentWorkoutTemplateId is not null)
        {
            DomainException.Require(
                workouts.Any(workout => workout.Id == currentWorkoutTemplateId),
                "The progress pointer must name a workout inside this program.");
        }

        CurrentWorkoutTemplateId = currentWorkoutTemplateId;
    }

    public Guid Id { get; }

    public string Name { get; }

    public IReadOnlyList<TrainingWeek> Weeks { get; }

    /// <summary>
    /// The next unit that has not been trained yet, or null once the program has run out —
    /// which is the same moment its cycle is finished.
    /// </summary>
    public Guid? CurrentWorkoutTemplateId { get; private set; }

    /// <summary>Every unit in program order: week 1 day I, day II, day III, week 2 day I, ...</summary>
    public IEnumerable<WorkoutTemplate> Workouts =>
        Weeks.OrderBy(week => week.WeekNumber)
            .SelectMany(week => week.Workouts.OrderBy(workout => workout.DayNumber));

    public WorkoutTemplate? FindWorkout(Guid workoutTemplateId) =>
        Workouts.FirstOrDefault(workout => workout.Id == workoutTemplateId);

    public TrainingWeek? FindWeekOf(Guid workoutTemplateId) =>
        Weeks.FirstOrDefault(week => week.Workouts.Any(workout => workout.Id == workoutTemplateId));

    internal void MoveProgressTo(Guid? workoutTemplateId)
    {
        if (workoutTemplateId is not null)
        {
            DomainException.Require(
                FindWorkout(workoutTemplateId.Value) is not null,
                "The progress pointer must name a workout inside this program.");
        }

        CurrentWorkoutTemplateId = workoutTemplateId;
    }
}
