using GriffGym.Application.Common;
using GriffGym.Domain.Training;
using GriffGym.Domain.Users;
using GriffGym.Domain.Workouts;

namespace GriffGym.Application.Abstractions.Persistence;

/*
 * These are not a generic IRepository<T> with Get/GetAll/Add/Update/Delete. Every method here
 * exists because a use case needs exactly it, and almost every read takes a userId — not
 * because a base class demanded a parameter, but because "whose data is this?" is a question
 * the persistence layer must never be able to skip.
 */

public interface IUserRepository
{
    Task<User?> FindByIdAsync(Guid id, CancellationToken cancellationToken);

    Task<User?> FindByNormalizedEmailAsync(string normalizedEmail, CancellationToken cancellationToken);

    Task<User?> FindByGoogleSubjectIdAsync(string googleSubjectId, CancellationToken cancellationToken);

    Task<bool> EmailExistsAsync(string normalizedEmail, CancellationToken cancellationToken);

    /// <summary>
    /// The security stamp of an account that still exists, or <c>null</c> when it does not.
    ///
    /// A projection rather than <see cref="FindByIdAsync"/> because this runs on every
    /// authenticated request: it answers "is the account this token names still real, and is
    /// this token still current for it?" and materialising the aggregate to read one string
    /// would put a wholly unnecessary cost on the hot path.
    /// </summary>
    Task<string?> FindSecurityStampAsync(Guid id, CancellationToken cancellationToken);

    void Add(User user);

    /// <summary>
    /// Removes the account row itself. Returns whether there was one to remove.
    ///
    /// A hard delete, not a tombstone. Tombstones exist so that an offline device can be told a
    /// record went away; nobody is left to tell, and leaving the row behind would mean the
    /// lifter asked for their data to be erased and it was not.
    /// </summary>
    Task<bool> DeleteAsync(Guid id, CancellationToken cancellationToken);
}

public interface IRefreshTokenRepository
{
    Task<RefreshToken?> FindByHashAsync(string tokenHash, CancellationToken cancellationToken);

    Task<IReadOnlyList<RefreshToken>> ListActiveForUserAsync(
        Guid userId,
        DateTimeOffset now,
        CancellationToken cancellationToken);

    void Add(RefreshToken token);

    /// <summary>
    /// Deletes every session this account holds, revoked and expired ones included. Returns the
    /// number of rows removed.
    /// </summary>
    Task<int> DeleteAllForUserAsync(Guid userId, CancellationToken cancellationToken);
}

public interface IReferenceMaxRepository
{
    Task<IReadOnlyList<ReferenceMax>> ListForUserAsync(Guid userId, CancellationToken cancellationToken);

    Task<ReferenceMax?> FindForUserAsync(Guid userId, LiftType lift, CancellationToken cancellationToken);

    void Add(ReferenceMax referenceMax);

    /// <summary>Deletes every reference max this account owns. Returns the row count.</summary>
    Task<int> DeleteAllForUserAsync(Guid userId, CancellationToken cancellationToken);
}

public interface IExerciseRepository
{
    Task<IReadOnlyList<Exercise>> ListForUserAsync(Guid userId, CancellationToken cancellationToken);

    Task<IReadOnlyList<Exercise>> ListForUserAsync(
        Guid userId,
        IReadOnlyCollection<Guid> ids,
        CancellationToken cancellationToken);

    void Add(Exercise exercise);

    /// <summary>
    /// Deletes this account's movement catalogue. Returns the row count.
    ///
    /// The catalogue is per lifter, not a shared dictionary, so there is nothing global to
    /// protect here — but it is still the last of an account's tables that can go, because a
    /// plan that prescribes a movement holds a <c>RESTRICT</c> reference to it.
    /// </summary>
    Task<int> DeleteAllForUserAsync(Guid userId, CancellationToken cancellationToken);
}

public interface ITrainingCycleRepository
{
    /// <summary>The full aggregate, program tree included. Scoped to the owner, always.</summary>
    Task<TrainingCycle?> FindForUserAsync(Guid userId, Guid cycleId, CancellationToken cancellationToken);

    /// <summary>Newest first: the cycle being trained, then the history behind it.</summary>
    Task<IReadOnlyList<TrainingCycle>> ListForUserAsync(Guid userId, CancellationToken cancellationToken);

    /// <summary>The highest-numbered cycle — the one the lifter is in or has just finished.</summary>
    Task<TrainingCycle?> FindCurrentForUserAsync(Guid userId, CancellationToken cancellationToken);

    Task<bool> CycleNumberExistsAsync(Guid userId, int cycleNumber, CancellationToken cancellationToken);

    /// <summary>
    /// Existence check by id alone, used to make creation idempotent: a phone that retried a
    /// POST after a timeout must not end up with two cycles.
    /// </summary>
    Task<bool> ExistsAsync(Guid cycleId, CancellationToken cancellationToken);

    void Add(TrainingCycle cycle);

    /// <summary>
    /// Deletes every cycle this account owns, and with it the whole plan hanging off each one —
    /// program, weeks, workout templates, exercise templates and planned sets, by cascade.
    /// Returns the number of cycles removed, not the size of the tree.
    /// </summary>
    Task<int> DeleteAllForUserAsync(Guid userId, CancellationToken cancellationToken);
}

public interface IWorkoutSessionRepository
{
    Task<WorkoutSession?> FindForUserAsync(
        Guid userId,
        Guid sessionId,
        CancellationToken cancellationToken);

    /// <summary>
    /// The session the lifter is in the middle of. There is at most one: starting a second
    /// while one is running is rejected, because "which workout am I in?" must have one answer.
    /// </summary>
    Task<WorkoutSession?> FindActiveForUserAsync(Guid userId, CancellationToken cancellationToken);

    Task<PagedResult<WorkoutSession>> ListForUserAsync(
        Guid userId,
        WorkoutHistoryFilter filter,
        PageRequest page,
        CancellationToken cancellationToken);

    /// <summary>Every session, oldest first — the input for a full restore.</summary>
    Task<IReadOnlyList<WorkoutSession>> ListAllForUserAsync(
        Guid userId,
        CancellationToken cancellationToken);

    Task<bool> ExistsAsync(Guid sessionId, CancellationToken cancellationToken);

    /// <summary>
    /// How many sessions were completed in each week of each cycle, counted in the database.
    ///
    /// Cycle progress is derived from the training log on every read rather than tracked as its
    /// own state, so it cannot drift away from what was actually trained. Materialising every
    /// session just to count them would be absurd once a lifter has a few years of history.
    /// </summary>
    Task<IReadOnlyList<CompletedWorkoutCount>> CountCompletedByWeekAsync(
        Guid userId,
        CancellationToken cancellationToken);

    void Add(WorkoutSession session);

    /// <summary>
    /// Deletes this account's entire training log — every session, and by cascade every exercise
    /// log and set log inside it. Returns the number of sessions removed.
    /// </summary>
    Task<int> DeleteAllForUserAsync(Guid userId, CancellationToken cancellationToken);
}

/// <summary>Completed sessions in one week of one cycle.</summary>
public sealed record CompletedWorkoutCount(Guid TrainingCycleId, int WeekNumber, int Count);

/// <summary>
/// What to narrow the history to. Every field is optional; the shape exists so that cycle,
/// status and date filters can be added to the API later without changing this signature again.
/// </summary>
public sealed record WorkoutHistoryFilter(
    Guid? TrainingCycleId = null,
    Domain.Training.WorkoutSessionStatus? Status = null,
    DateOnly? From = null,
    DateOnly? To = null);

/// <summary>
/// One unit of work over the whole request.
///
/// Creating a cycle writes the cycle, its program, six weeks, eighteen workouts and every
/// planned set inside them. Half of that is a lifter with no plan they can train, so it is one
/// transaction or none of it.
/// </summary>
public interface IUnitOfWork
{
    Task SaveChangesAsync(CancellationToken cancellationToken);

    Task<T> ExecuteInTransactionAsync<T>(
        Func<CancellationToken, Task<T>> operation,
        CancellationToken cancellationToken);
}
