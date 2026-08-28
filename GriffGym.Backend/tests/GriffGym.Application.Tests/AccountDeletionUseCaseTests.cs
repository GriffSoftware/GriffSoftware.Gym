using GriffGym.Application.Common;
using GriffGym.Application.Users;
using GriffGym.Domain.Training;
using GriffGym.Domain.Users;

namespace GriffGym.Application.Tests;

/// <summary>
/// The parts of account deletion that are decisions rather than SQL: whose account goes, and
/// that "whose" is never anything but the signed-in one.
///
/// What the transaction actually does to PostgreSQL is checked where it can be checked for
/// real — <c>AccountDeletionTransactionTests</c> in the infrastructure suite and
/// <c>AccountDeletionTests</c> against the live API. A fake unit of work cannot roll anything
/// back, so a test written here that claimed to prove atomicity would prove nothing.
/// </summary>
public sealed class AccountDeletionUseCaseTests
{
    private static readonly DateTimeOffset Now = new(2026, 3, 2, 18, 0, 0, TimeSpan.Zero);

    private readonly Guid _leavingId = Guid.Parse("11111111-1111-1111-1111-111111111111");
    private readonly Guid _stayingId = Guid.Parse("22222222-2222-2222-2222-222222222222");

    private readonly FakeUserRepository _users = new();
    private readonly FakeRefreshTokenRepository _refreshTokens = new();
    private readonly FakeWorkoutSessionRepository _sessions = new();
    private readonly FakeTrainingCycleRepository _cycles = new();
    private readonly FakeExerciseRepository _exercises = new();
    private readonly FakeReferenceMaxRepository _referenceMaxes = new();
    private readonly FakeCurrentUser _currentUser = new();

    [Fact]
    public async Task It_deletes_the_signed_in_account_and_everything_it_owns()
    {
        GiveBothLiftersData();
        _currentUser.UserId = _leavingId;

        var summary = await UseCase().ExecuteAsync(default);

        Assert.True(summary.AccountExisted);
        Assert.Equal(1, summary.Exercises);
        Assert.Equal(1, summary.ReferenceMaxes);
        Assert.Equal(1, summary.RefreshTokens);

        Assert.DoesNotContain(_users.All, user => user.Id == _leavingId);
        Assert.DoesNotContain(_refreshTokens.All, token => token.UserId == _leavingId);
        Assert.Empty(await _exercises.ListForUserAsync(_leavingId, default));
        Assert.Empty(await _referenceMaxes.ListForUserAsync(_leavingId, default));
        Assert.Empty(await _cycles.ListForUserAsync(_leavingId, default));
        Assert.Empty(await _sessions.ListAllForUserAsync(_leavingId, default));
    }

    [Fact]
    public async Task It_leaves_every_other_account_alone()
    {
        GiveBothLiftersData();
        _currentUser.UserId = _leavingId;

        await UseCase().ExecuteAsync(default);

        Assert.Contains(_users.All, user => user.Id == _stayingId);
        Assert.Contains(_refreshTokens.All, token => token.UserId == _stayingId);
        Assert.Single(await _exercises.ListForUserAsync(_stayingId, default));
        Assert.Single(await _referenceMaxes.ListForUserAsync(_stayingId, default));
    }

    [Fact]
    public async Task It_refuses_a_request_that_names_nobody()
    {
        GiveBothLiftersData();
        _currentUser.UserId = null;

        await Assert.ThrowsAsync<UnauthenticatedException>(() => UseCase().ExecuteAsync(default));

        // Nothing was touched on the way to refusing.
        Assert.Equal(2, _users.All.Count);
        Assert.Equal(2, _refreshTokens.All.Count);
    }

    [Fact]
    public async Task Deleting_an_account_that_is_already_gone_is_not_an_error()
    {
        // A phone that retried after a timeout, or two taps that both got through. The second
        // pass has nothing left to remove and says so rather than throwing.
        GiveBothLiftersData();
        _currentUser.UserId = _leavingId;

        await UseCase().ExecuteAsync(default);
        var second = await UseCase().ExecuteAsync(default);

        Assert.False(second.AccountExisted);
        Assert.Equal(0, second.WorkoutSessions);
        Assert.Equal(0, second.RefreshTokens);
    }

    private DeleteCurrentUserAccountUseCase UseCase() => new(
        _currentUser,
        _users,
        _refreshTokens,
        _sessions,
        _cycles,
        _exercises,
        _referenceMaxes,
        new FakeUnitOfWork());

    private void GiveBothLiftersData()
    {
        Populate(_leavingId, "leaving@example.com");
        Populate(_stayingId, "staying@example.com");
    }

    private void Populate(Guid userId, string email)
    {
        _users.Add(User.Register(userId, EmailAddress.Of(email), "hashed:pw", Now));

        _refreshTokens.Add(RefreshToken.Issue(
            Guid.NewGuid(), userId, $"sha256:{userId}", "pixel-9", Now, TimeSpan.FromDays(30)));

        var exerciseId = Guid.NewGuid();
        _exercises.Add(Exercise.Create(
            exerciseId, userId, "Squat", ExerciseCategory.Squat, Now));

        _referenceMaxes.Add(ReferenceMax.Create(
            Guid.NewGuid(), userId, LiftType.Squat, Weight.Of(210m), Now));
    }
}
