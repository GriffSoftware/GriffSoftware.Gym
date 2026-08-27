using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Common;
using GriffGym.Domain.Training;

namespace GriffGym.Application.Workouts;

public sealed class GetWorkoutSessionUseCase(
    IWorkoutSessionRepository sessions,
    ICurrentUser currentUser)
{
    public async Task<WorkoutSessionView> ExecuteAsync(
        Guid sessionId,
        CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();

        // Scoped to the owner inside the query itself, not filtered afterwards. A workout that
        // belongs to somebody else is indistinguishable from one that does not exist.
        var session = await sessions.FindForUserAsync(userId, sessionId, cancellationToken)
                      ?? throw new NotFoundException("Workout", sessionId);

        return WorkoutMapper.ToView(session);
    }
}

/// <summary>
/// The workout the lifter is in the middle of, if there is one.
///
/// This is what makes a session survive a new phone: sign in, ask, carry on from the set you
/// were on.
/// </summary>
public sealed class GetActiveWorkoutUseCase(
    IWorkoutSessionRepository sessions,
    ICurrentUser currentUser)
{
    public async Task<WorkoutSessionView?> ExecuteAsync(CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();
        var session = await sessions.FindActiveForUserAsync(userId, cancellationToken);

        return session is null ? null : WorkoutMapper.ToView(session);
    }
}

public sealed record WorkoutHistoryQuery(
    int Page = 1,
    int PageSize = PageRequest.DefaultPageSize,
    Guid? TrainingCycleId = null,
    WorkoutSessionStatus? Status = null,
    DateOnly? From = null,
    DateOnly? To = null);

/// <summary>
/// Paginated history, newest first.
///
/// Never unbounded: a lifter several years in has hundreds of sessions and tens of thousands
/// of sets, and no client wants that in one response.
/// </summary>
public sealed class GetWorkoutHistoryUseCase(
    IWorkoutSessionRepository sessions,
    ICurrentUser currentUser)
{
    public async Task<PagedResult<WorkoutSessionSummaryView>> ExecuteAsync(
        WorkoutHistoryQuery query,
        CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();

        var page = await sessions.ListForUserAsync(
            userId,
            new WorkoutHistoryFilter(query.TrainingCycleId, query.Status, query.From, query.To),
            new PageRequest(query.Page, query.PageSize),
            cancellationToken);

        return new PagedResult<WorkoutSessionSummaryView>(
            [.. page.Items.Select(WorkoutMapper.ToSummary)],
            page.Page,
            page.PageSize,
            page.TotalCount);
    }
}
