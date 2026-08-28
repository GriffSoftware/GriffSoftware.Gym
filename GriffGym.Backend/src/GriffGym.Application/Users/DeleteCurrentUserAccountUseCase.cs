using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;

namespace GriffGym.Application.Users;

/// <summary>How much of an account was actually removed. Returned so it can be logged.</summary>
public sealed record AccountDeletionSummary(
    bool AccountExisted,
    int WorkoutSessions,
    int TrainingCycles,
    int Exercises,
    int ReferenceMaxes,
    int RefreshTokens);

/// <summary>
/// Erases one lifter's account and everything it owns.
///
/// Whose account is never a parameter. It comes from the validated access token's subject and
/// nowhere else, the same as every other authenticated operation — an endpoint that accepted a
/// user id would be an endpoint for deleting somebody else's training history.
///
/// Two properties this has to have, and they pull in different directions:
///
/// <list type="bullet">
/// <item>
/// <b>Complete.</b> The point of the feature is that a lifter can genuinely get their data out
/// of the system, so this is a hard delete and not a tombstone. A soft delete that left the
/// whole training dataset in the active database would be a deletion button that does not
/// delete.
/// </item>
/// <item>
/// <b>Atomic.</b> Everything or nothing. "Workouts deleted, cycles deleted, then a failure, and
/// the account still exists" is a lifter who has lost their history and kept their account;
/// "user deleted, training data orphaned" is worse still, because those rows would then have no
/// owner and no route to ever being removed.
/// </item>
/// </list>
///
/// The order below is the ownership graph read from the leaves inwards, and it is not
/// arbitrary. <c>exercise_template</c> references <c>exercise</c> with <c>RESTRICT</c> — the
/// deliberate rule that a plan prescribing a movement is a reason not to delete that movement.
/// Cycles therefore have to go before the catalogue does. Relying on the <c>user</c> row's own
/// cascades instead would leave that ordering to whatever sequence PostgreSQL happens to pick
/// for two sibling cascade paths, which is not something to bet a data-deletion guarantee on.
///
/// Anything added to the ownership graph later has to be added here too, and the absence of a
/// line in this method is the kind of thing a diff makes visible.
/// </summary>
public sealed class DeleteCurrentUserAccountUseCase(
    ICurrentUser currentUser,
    IUserRepository users,
    IRefreshTokenRepository refreshTokens,
    IWorkoutSessionRepository workoutSessions,
    ITrainingCycleRepository trainingCycles,
    IExerciseRepository exercises,
    IReferenceMaxRepository referenceMaxes,
    IUnitOfWork unitOfWork)
{
    public Task<AccountDeletionSummary> ExecuteAsync(CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();

        return unitOfWork.ExecuteInTransactionAsync(
            async token =>
            {
                // The training log first: sessions, and by cascade their exercise logs and sets.
                var sessions = await workoutSessions.DeleteAllForUserAsync(userId, token);

                // Then the plans: cycles, and by cascade programs, weeks, workout templates,
                // exercise templates and planned sets. This is what releases the RESTRICT hold
                // on the movement catalogue.
                var cycles = await trainingCycles.DeleteAllForUserAsync(userId, token);

                // Only now is the catalogue unreferenced.
                var movements = await exercises.DeleteAllForUserAsync(userId, token);

                var maxes = await referenceMaxes.DeleteAllForUserAsync(userId, token);

                // Every session on every device, live or already revoked. After this no refresh
                // token in existence can mint a new access token for this account.
                var sessionsRevoked = await refreshTokens.DeleteAllForUserAsync(userId, token);

                // Last, so that a failure anywhere above rolls back with the account intact
                // rather than leaving data nobody owns.
                var existed = await users.DeleteAsync(userId, token);

                return new AccountDeletionSummary(
                    existed,
                    sessions,
                    cycles,
                    movements,
                    maxes,
                    sessionsRevoked);
            },
            cancellationToken);
    }
}
