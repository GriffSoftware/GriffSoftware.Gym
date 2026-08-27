using GriffGym.Domain.Common;
using GriffGym.Domain.Training;

namespace GriffGym.Domain.Workouts;

/// <summary>
/// A workout in flight, or one in the history book.
///
/// A session is a *snapshot* of a <see cref="WorkoutTemplate"/>, not a pointer to it. Week,
/// day, title and the deload flag are copied in; the link back to the template is provenance
/// and may be null. That is what keeps history immune to later edits of the program, and it is
/// the single most important rule in this model.
///
/// Sessions can also arrive already finished. A lifter who trained locally for six months
/// before creating an account uploads real history, and refusing anything but IN_PROGRESS
/// would make that impossible.
/// </summary>
public sealed class WorkoutSession : Entity
{
    public const int MaxTitleLength = 200;
    public const int MaxNotesLength = 2000;

    private WorkoutSession(
        Guid id,
        Guid userId,
        Guid? trainingCycleId,
        Guid? trainingWeekId,
        Guid? workoutTemplateId,
        int weekNumber,
        int dayNumber,
        string title,
        bool isDeload,
        WorkoutSessionStatus status,
        DateOnly performedOn,
        DateTimeOffset startedAtUtc,
        DateTimeOffset? finishedAtUtc,
        TrainingVolume? totalVolume,
        string? notes,
        List<ExerciseLog> exercises,
        DateTimeOffset createdAtUtc,
        DateTimeOffset updatedAtUtc)
        : base(id, createdAtUtc, updatedAtUtc)
    {
        DomainException.Require(userId != Guid.Empty, "A workout session must belong to a user.");
        DomainException.Require(weekNumber >= 1, $"Week numbers start at one, got {weekNumber}.");
        DomainException.Require(dayNumber >= 1, $"Day numbers start at one, got {dayNumber}.");
        DomainException.Require(!string.IsNullOrWhiteSpace(title), "A workout session needs a title.");
        DomainException.Require(
            status.IsFinished() == (finishedAtUtc is not null),
            $"A session has a finish time exactly when it is finished, got {status} / {finishedAtUtc}.");
        DomainException.Require(
            finishedAtUtc is null || finishedAtUtc >= startedAtUtc,
            "A session cannot finish before it started.");
        DomainException.Require(
            exercises.Select(exercise => exercise.Position).Distinct().Count() == exercises.Count,
            "A session has two exercises at the same position.");
        DomainException.Require(
            exercises.Select(exercise => exercise.Id).Distinct().Count() == exercises.Count,
            "A session has two exercises with the same identifier.");

        UserId = userId;
        TrainingCycleId = trainingCycleId;
        TrainingWeekId = trainingWeekId;
        WorkoutTemplateId = workoutTemplateId;
        WeekNumber = weekNumber;
        DayNumber = dayNumber;
        Title = Truncate(title.Trim(), MaxTitleLength);
        IsDeload = isDeload;
        Status = status;
        PerformedOn = performedOn;
        StartedAtUtc = startedAtUtc;
        FinishedAtUtc = finishedAtUtc;
        FrozenTotalVolume = totalVolume;
        Notes = TrimNotes(notes);
        _exercises = [.. exercises.OrderBy(exercise => exercise.Position)];
    }

    private readonly List<ExerciseLog> _exercises;

    public Guid UserId { get; }

    public Guid? TrainingCycleId { get; }

    public Guid? TrainingWeekId { get; }

    /// <summary>Provenance only. Nulled rather than cascaded if the template ever disappears.</summary>
    public Guid? WorkoutTemplateId { get; }

    public int WeekNumber { get; }

    public int DayNumber { get; }

    public string Title { get; }

    public bool IsDeload { get; }

    public WorkoutSessionStatus Status { get; private set; }

    /// <summary>The training day as the lifter's calendar sees it, not as UTC sees it.</summary>
    public DateOnly PerformedOn { get; }

    public DateTimeOffset StartedAtUtc { get; }

    public DateTimeOffset? FinishedAtUtc { get; private set; }

    public string? Notes { get; private set; }

    public IReadOnlyList<ExerciseLog> Exercises => _exercises;

    /// <summary>
    /// Tonnage frozen at completion. While a session is live the number is recomputed from the
    /// sets on every read, so it can never drift away from the log.
    /// </summary>
    private TrainingVolume? FrozenTotalVolume { get; set; }

    public TrainingVolume TotalVolume => FrozenTotalVolume ?? ComputeVolume();

    public bool IsActive => Status == WorkoutSessionStatus.InProgress;

    public bool IsReadOnly => Status.IsFinished();

    public TimeSpan? Duration => FinishedAtUtc - StartedAtUtc;

    public int TotalSets => _exercises.Sum(exercise => exercise.Sets.Count);

    public int CompletedSets =>
        _exercises.Sum(exercise => exercise.Sets.Count(set => set.Completed));

    public int TotalReps =>
        _exercises.Sum(exercise =>
            exercise.Sets.Where(set => set.Completed).Sum(set => set.ActualReps ?? 0));

    /// <summary>Snapshots a planned unit of the program into a live session.</summary>
    public static WorkoutSession StartFromTemplate(
        Guid id,
        Guid userId,
        TrainingCycle cycle,
        WorkoutTemplate template,
        DateOnly performedOn,
        DateTimeOffset startedAtUtc,
        DateTimeOffset now,
        Func<Guid> newExerciseLogId,
        Func<Guid> newSetLogId)
    {
        var week = cycle.Program.FindWeekOf(template.Id)
                   ?? throw new DomainException("The workout template does not belong to this cycle.");

        var exercises = template.Exercises
            .Select(exercise => ExerciseLog.FromTemplate(exercise, newExerciseLogId, newSetLogId))
            .ToList();

        return new WorkoutSession(
            id,
            userId,
            cycle.Id,
            week.Id,
            template.Id,
            week.WeekNumber,
            template.DayNumber,
            template.Title,
            week.IsDeload,
            WorkoutSessionStatus.InProgress,
            performedOn,
            startedAtUtc,
            finishedAtUtc: null,
            totalVolume: null,
            notes: null,
            exercises,
            now,
            now);
    }

    /// <summary>
    /// Builds a session from data the client already holds — a live workout it started
    /// offline, or a finished one it is backfilling after creating an account.
    /// </summary>
    public static WorkoutSession Create(
        Guid id,
        Guid userId,
        Guid? trainingCycleId,
        Guid? trainingWeekId,
        Guid? workoutTemplateId,
        int weekNumber,
        int dayNumber,
        string title,
        bool isDeload,
        WorkoutSessionStatus status,
        DateOnly performedOn,
        DateTimeOffset startedAtUtc,
        DateTimeOffset? finishedAtUtc,
        string? notes,
        IReadOnlyList<ExerciseLog> exercises,
        DateTimeOffset now)
    {
        var session = new WorkoutSession(
            id,
            userId,
            trainingCycleId,
            trainingWeekId,
            workoutTemplateId,
            weekNumber,
            dayNumber,
            title,
            isDeload,
            status,
            performedOn,
            startedAtUtc,
            finishedAtUtc,
            totalVolume: null,
            notes,
            [.. exercises],
            now,
            now);

        if (status.IsFinished())
        {
            session.FrozenTotalVolume = session.ComputeVolume();
        }

        return session;
    }

    public static WorkoutSession FromStorage(
        Guid id,
        Guid userId,
        Guid? trainingCycleId,
        Guid? trainingWeekId,
        Guid? workoutTemplateId,
        int weekNumber,
        int dayNumber,
        string title,
        bool isDeload,
        WorkoutSessionStatus status,
        DateOnly performedOn,
        DateTimeOffset startedAtUtc,
        DateTimeOffset? finishedAtUtc,
        decimal? totalVolumeKg,
        string? notes,
        IReadOnlyList<ExerciseLog> exercises,
        DateTimeOffset createdAtUtc,
        DateTimeOffset updatedAtUtc) =>
        new(
            id,
            userId,
            trainingCycleId,
            trainingWeekId,
            workoutTemplateId,
            weekNumber,
            dayNumber,
            title,
            isDeload,
            status,
            performedOn,
            startedAtUtc,
            finishedAtUtc,
            totalVolumeKg is null ? null : TrainingVolume.Of(totalVolumeKg.Value),
            notes,
            [.. exercises],
            createdAtUtc,
            updatedAtUtc);

    /// <summary>Writes one lifter-entered result. Planned values are never touched.</summary>
    public void LogSet(Guid setLogId, SetResult result, DateTimeOffset now)
    {
        RequireEditable();

        var set = _exercises
            .Select(exercise => exercise.FindSet(setLogId))
            .FirstOrDefault(found => found is not null)
            ?? throw new DomainException($"Set {setLogId} does not belong to this session.");

        set.Record(result);
        Touch(now);
    }

    public void UpdateNotes(string? notes, DateTimeOffset now)
    {
        RequireEditable();

        Notes = TrimNotes(notes);
        Touch(now);
    }

    public void UpdateExerciseNotes(Guid exerciseLogId, string? notes, DateTimeOffset now)
    {
        RequireEditable();

        var exercise = _exercises.FirstOrDefault(candidate => candidate.Id == exerciseLogId)
                       ?? throw new DomainException(
                           $"Exercise {exerciseLogId} does not belong to this session.");

        exercise.UpdateNotes(notes);
        Touch(now);
    }

    /// <summary>
    /// Replaces the whole logged tree with what the client holds.
    ///
    /// This is the offline-first upload path: the phone is the source of truth for a session it
    /// is in the middle of, and sending the tree wholesale is both idempotent and immune to
    /// lost intermediate requests.
    /// </summary>
    public void ReplaceExercises(IReadOnlyList<ExerciseLog> exercises, DateTimeOffset now)
    {
        RequireEditable();

        DomainException.Require(
            exercises.Select(exercise => exercise.Position).Distinct().Count() == exercises.Count,
            "A session has two exercises at the same position.");
        DomainException.Require(
            exercises.Select(exercise => exercise.Id).Distinct().Count() == exercises.Count,
            "A session has two exercises with the same identifier.");

        _exercises.Clear();
        _exercises.AddRange(exercises.OrderBy(exercise => exercise.Position));
        Touch(now);
    }

    public void Complete(DateTimeOffset finishedAtUtc, DateTimeOffset now)
    {
        RequireEditable();

        DomainException.Require(
            finishedAtUtc >= StartedAtUtc,
            "A session cannot finish before it started.");

        Status = WorkoutSessionStatus.Completed;
        FinishedAtUtc = finishedAtUtc;
        FrozenTotalVolume = ComputeVolume();
        Touch(now);
    }

    public void Cancel(DateTimeOffset finishedAtUtc, DateTimeOffset now)
    {
        RequireEditable();

        DomainException.Require(
            finishedAtUtc >= StartedAtUtc,
            "A session cannot finish before it started.");

        Status = WorkoutSessionStatus.Cancelled;
        FinishedAtUtc = finishedAtUtc;
        FrozenTotalVolume = ComputeVolume();
        Touch(now);
    }

    /// <summary>Best Epley estimate the session produced for one of the big three.</summary>
    public Weight? BestEstimatedOneRepMax(ExerciseCategory category) =>
        _exercises
            .Where(exercise => exercise.IsMainLift && exercise.ExerciseCategory == category)
            .Select(exercise => exercise.BestEstimatedOneRepMax)
            .Where(estimate => estimate is not null)
            .OrderByDescending(estimate => estimate!.Value.Kilograms)
            .FirstOrDefault();

    private void RequireEditable() =>
        DomainException.Require(
            IsActive,
            $"'{Title}' is {Status.ToString().ToUpperInvariant()} and can no longer be changed.");

    private TrainingVolume ComputeVolume() =>
        _exercises.Aggregate(TrainingVolume.Zero, (total, exercise) => total + exercise.Volume);

    private static string Truncate(string value, int maxLength) =>
        value.Length <= maxLength ? value : value[..maxLength];

    private static string? TrimNotes(string? notes)
    {
        if (string.IsNullOrWhiteSpace(notes))
        {
            return null;
        }

        return Truncate(notes.Trim(), MaxNotesLength);
    }
}
