using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Abstractions.Security;
using GriffGym.Application.Common;
using GriffGym.Domain.Training;
using GriffGym.Domain.Users;
using GriffGym.Domain.Workouts;

namespace GriffGym.Application.Tests;

/*
 * Hand-written test doubles rather than a mocking framework.
 *
 * These are not stubs that return canned values: they are working in-memory implementations
 * that enforce the same rules the real ones do — a repository scoped to a user really does
 * refuse to hand back somebody else's cycle. That means a use case test can assert on behaviour
 * instead of on which methods were called, and it means these fakes catch the ownership bugs
 * that "verify(repository).GetById(id)" would sail straight past.
 */

internal sealed class FakeClock(DateTimeOffset now) : IClock
{
    public DateTimeOffset UtcNow { get; private set; } = now;

    public void Advance(TimeSpan by) => UtcNow = UtcNow.Add(by);
}

/// <summary>Predictable, ordered identifiers, so a failure message is readable.</summary>
internal sealed class FakeIdentifierFactory : IIdentifierFactory
{
    private int _next;

    public Guid NewId() => new(++_next, 0, 0, [0, 0, 0, 0, 0, 0, 0, 0]);
}

internal sealed class FakeCurrentUser(Guid? userId = null) : ICurrentUser
{
    public Guid? UserId { get; set; } = userId;

    public bool IsAuthenticated => UserId is not null;
}

internal sealed class FakeUnitOfWork : IUnitOfWork
{
    public int SaveCount { get; private set; }

    public Task SaveChangesAsync(CancellationToken cancellationToken)
    {
        SaveCount++;
        return Task.CompletedTask;
    }

    public Task<T> ExecuteInTransactionAsync<T>(
        Func<CancellationToken, Task<T>> operation,
        CancellationToken cancellationToken) => operation(cancellationToken);
}

/// <summary>
/// Reversible "hashing": enough to tell a right password from a wrong one without spending
/// PBKDF2's work factor on every test.
/// </summary>
internal sealed class FakePasswordHasher : IPasswordHasher
{
    public bool ReportRehashNeeded { get; set; }

    public string Hash(string password) => $"hashed:{password}";

    public PasswordVerificationOutcome Verify(string passwordHash, string providedPassword)
    {
        if (passwordHash != $"hashed:{providedPassword}")
        {
            return PasswordVerificationOutcome.Failed;
        }

        return ReportRehashNeeded
            ? PasswordVerificationOutcome.SuccessRehashNeeded
            : PasswordVerificationOutcome.Success;
    }
}

internal sealed class FakeAccessTokenIssuer(IClock clock) : IAccessTokenIssuer
{
    public AccessToken Issue(User user) => new(
        $"access-for-{user.Id}",
        clock.UtcNow.AddMinutes(15),
        TimeSpan.FromMinutes(15));
}

internal sealed class FakeGoogleIdTokenValidator : IGoogleIdTokenValidator
{
    /// <summary>What the next call returns; null makes it fail the way a bad token really would.</summary>
    public GoogleIdentity? NextIdentity { get; set; }

    public Task<GoogleIdentity> ValidateAsync(string idToken, CancellationToken cancellationToken) =>
        NextIdentity is { } identity
            ? Task.FromResult(identity)
            : throw new AuthenticationFailedException("Invalid Google credential.");
}

internal sealed class FakeRefreshTokenGenerator : IRefreshTokenGenerator
{
    private int _next;

    public RefreshTokenMaterial Generate()
    {
        var value = $"refresh-{++_next}";
        return new RefreshTokenMaterial(value, HashPresented(value));
    }

    public string HashPresented(string token) => $"sha256:{token}";
}

internal sealed class FakeUserRepository : IUserRepository
{
    private readonly List<User> _users = [];

    public IReadOnlyList<User> All => _users;

    public Task<User?> FindByIdAsync(Guid id, CancellationToken cancellationToken) =>
        Task.FromResult(_users.FirstOrDefault(user => user.Id == id));

    public Task<User?> FindByNormalizedEmailAsync(
        string normalizedEmail,
        CancellationToken cancellationToken) =>
        Task.FromResult(_users.FirstOrDefault(user => user.Email.Normalized == normalizedEmail));

    public Task<User?> FindByGoogleSubjectIdAsync(
        string googleSubjectId,
        CancellationToken cancellationToken) =>
        Task.FromResult(_users.FirstOrDefault(user => user.GoogleSubjectId == googleSubjectId));

    public Task<bool> EmailExistsAsync(string normalizedEmail, CancellationToken cancellationToken) =>
        Task.FromResult(_users.Any(user => user.Email.Normalized == normalizedEmail));

    public Task<string?> FindSecurityStampAsync(Guid id, CancellationToken cancellationToken) =>
        Task.FromResult(_users.FirstOrDefault(user => user.Id == id)?.SecurityStamp);

    public void Add(User user) => _users.Add(user);

    public Task<bool> DeleteAsync(Guid id, CancellationToken cancellationToken) =>
        Task.FromResult(_users.RemoveAll(user => user.Id == id) > 0);
}

internal sealed class FakeRefreshTokenRepository : IRefreshTokenRepository
{
    private readonly List<RefreshToken> _tokens = [];

    public IReadOnlyList<RefreshToken> All => _tokens;

    public Task<RefreshToken?> FindByHashAsync(string tokenHash, CancellationToken cancellationToken) =>
        Task.FromResult(_tokens.FirstOrDefault(token => token.TokenHash == tokenHash));

    public Task<IReadOnlyList<RefreshToken>> ListActiveForUserAsync(
        Guid userId,
        DateTimeOffset now,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<RefreshToken>>(
            [.. _tokens.Where(token => token.UserId == userId && token.IsActiveAt(now))]);

    public void Add(RefreshToken token) => _tokens.Add(token);

    public Task<int> DeleteAllForUserAsync(Guid userId, CancellationToken cancellationToken) =>
        Task.FromResult(_tokens.RemoveAll(token => token.UserId == userId));
}

internal sealed class FakeReferenceMaxRepository : IReferenceMaxRepository
{
    private readonly List<ReferenceMax> _maxes = [];

    public Task<IReadOnlyList<ReferenceMax>> ListForUserAsync(
        Guid userId,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<ReferenceMax>>(
            [.. _maxes.Where(max => max.UserId == userId)]);

    public Task<ReferenceMax?> FindForUserAsync(
        Guid userId,
        LiftType lift,
        CancellationToken cancellationToken) =>
        Task.FromResult(_maxes.FirstOrDefault(max => max.UserId == userId && max.Lift == lift));

    public void Add(ReferenceMax referenceMax) => _maxes.Add(referenceMax);

    public Task<int> DeleteAllForUserAsync(Guid userId, CancellationToken cancellationToken) =>
        Task.FromResult(_maxes.RemoveAll(max => max.UserId == userId));
}

internal sealed class FakeExerciseRepository : IExerciseRepository
{
    private readonly List<Exercise> _exercises = [];

    public Task<IReadOnlyList<Exercise>> ListForUserAsync(
        Guid userId,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<Exercise>>(
            [.. _exercises.Where(exercise => exercise.UserId == userId)]);

    public Task<IReadOnlyList<Exercise>> ListForUserAsync(
        Guid userId,
        IReadOnlyCollection<Guid> ids,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<Exercise>>(
            [.. _exercises.Where(exercise => exercise.UserId == userId && ids.Contains(exercise.Id))]);

    public void Add(Exercise exercise) => _exercises.Add(exercise);

    public Task<int> DeleteAllForUserAsync(Guid userId, CancellationToken cancellationToken) =>
        Task.FromResult(_exercises.RemoveAll(exercise => exercise.UserId == userId));
}

internal sealed class FakeTrainingCycleRepository : ITrainingCycleRepository
{
    private readonly List<TrainingCycle> _cycles = [];

    public Task<TrainingCycle?> FindForUserAsync(
        Guid userId,
        Guid cycleId,
        CancellationToken cancellationToken) =>
        // Scoped to the owner, exactly as the real one is.
        Task.FromResult(_cycles.FirstOrDefault(
            cycle => cycle.Id == cycleId && cycle.UserId == userId));

    public Task<IReadOnlyList<TrainingCycle>> ListForUserAsync(
        Guid userId,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<TrainingCycle>>(
            [.. _cycles.Where(cycle => cycle.UserId == userId)
                .OrderByDescending(cycle => cycle.CycleNumber)]);

    public Task<TrainingCycle?> FindCurrentForUserAsync(
        Guid userId,
        CancellationToken cancellationToken) =>
        Task.FromResult(_cycles.Where(cycle => cycle.UserId == userId)
            .OrderByDescending(cycle => cycle.CycleNumber)
            .FirstOrDefault());

    public Task<bool> CycleNumberExistsAsync(
        Guid userId,
        int cycleNumber,
        CancellationToken cancellationToken) =>
        Task.FromResult(_cycles.Any(
            cycle => cycle.UserId == userId && cycle.CycleNumber == cycleNumber));

    public Task<bool> ExistsAsync(Guid cycleId, CancellationToken cancellationToken) =>
        Task.FromResult(_cycles.Any(cycle => cycle.Id == cycleId));

    public void Add(TrainingCycle cycle) => _cycles.Add(cycle);

    public Task<int> DeleteAllForUserAsync(Guid userId, CancellationToken cancellationToken) =>
        Task.FromResult(_cycles.RemoveAll(cycle => cycle.UserId == userId));
}

internal sealed class FakeWorkoutSessionRepository : IWorkoutSessionRepository
{
    private readonly List<WorkoutSession> _sessions = [];

    public IReadOnlyList<WorkoutSession> All => _sessions;

    public Task<WorkoutSession?> FindForUserAsync(
        Guid userId,
        Guid sessionId,
        CancellationToken cancellationToken) =>
        Task.FromResult(_sessions.FirstOrDefault(
            session => session.Id == sessionId && session.UserId == userId));

    public Task<WorkoutSession?> FindActiveForUserAsync(
        Guid userId,
        CancellationToken cancellationToken) =>
        Task.FromResult(_sessions.FirstOrDefault(session =>
            session.UserId == userId && session.Status == WorkoutSessionStatus.InProgress));

    public Task<PagedResult<WorkoutSession>> ListForUserAsync(
        Guid userId,
        WorkoutHistoryFilter filter,
        PageRequest page,
        CancellationToken cancellationToken)
    {
        var matching = _sessions
            .Where(session => session.UserId == userId)
            .Where(session => filter.TrainingCycleId is null
                              || session.TrainingCycleId == filter.TrainingCycleId)
            .Where(session => filter.Status is null || session.Status == filter.Status)
            .Where(session => filter.From is null || session.PerformedOn >= filter.From)
            .Where(session => filter.To is null || session.PerformedOn <= filter.To)
            .OrderByDescending(session => session.PerformedOn)
            .ThenByDescending(session => session.StartedAtUtc)
            .ToList();

        return Task.FromResult(new PagedResult<WorkoutSession>(
            [.. matching.Skip(page.Skip).Take(page.PageSize)],
            page.Page,
            page.PageSize,
            matching.Count));
    }

    public Task<IReadOnlyList<WorkoutSession>> ListAllForUserAsync(
        Guid userId,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<WorkoutSession>>(
            [.. _sessions.Where(session => session.UserId == userId)
                .OrderBy(session => session.StartedAtUtc)]);

    public Task<bool> ExistsAsync(Guid sessionId, CancellationToken cancellationToken) =>
        Task.FromResult(_sessions.Any(session => session.Id == sessionId));

    public Task<IReadOnlyList<CompletedWorkoutCount>> CountCompletedByWeekAsync(
        Guid userId,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<CompletedWorkoutCount>>(
            [.. _sessions
                .Where(session => session.UserId == userId
                                  && session.Status == WorkoutSessionStatus.Completed
                                  && session.TrainingCycleId is not null)
                .GroupBy(session => new { session.TrainingCycleId, session.WeekNumber })
                .Select(group => new CompletedWorkoutCount(
                    group.Key.TrainingCycleId!.Value,
                    group.Key.WeekNumber,
                    group.Count()))]);

    public void Add(WorkoutSession session) => _sessions.Add(session);

    public Task<int> DeleteAllForUserAsync(Guid userId, CancellationToken cancellationToken) =>
        Task.FromResult(_sessions.RemoveAll(session => session.UserId == userId));
}
