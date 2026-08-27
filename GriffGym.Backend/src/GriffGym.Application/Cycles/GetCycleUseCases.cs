using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Common;

namespace GriffGym.Application.Cycles;

/// <summary>
/// Every cycle with its week-by-week progress, newest first: the current one followed by the
/// history behind it.
///
/// One call rather than "the current cycle" plus "the rest", because a screen showing both at
/// once must never see them disagree about which cycle is which.
/// </summary>
public sealed class GetTrainingCyclesUseCase(
    ITrainingCycleRepository cycles,
    IWorkoutSessionRepository sessions,
    ICurrentUser currentUser)
{
    public async Task<IReadOnlyList<TrainingCycleSummaryView>> ExecuteAsync(
        CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();

        var stored = await cycles.ListForUserAsync(userId, cancellationToken);
        if (stored.Count == 0)
        {
            return [];
        }

        var completed = await sessions.CountCompletedByWeekAsync(userId, cancellationToken);
        var byCycle = completed
            .GroupBy(count => count.TrainingCycleId)
            .ToDictionary(
                group => group.Key,
                group => (IReadOnlyDictionary<int, int>)group.ToDictionary(
                    count => count.WeekNumber,
                    count => count.Count));

        return
        [
            .. stored
                .OrderByDescending(cycle => cycle.CycleNumber)
                .Select(cycle => CycleMapper.ToSummary(
                    cycle,
                    byCycle.GetValueOrDefault(cycle.Id) ?? new Dictionary<int, int>()))
        ];
    }
}

/// <summary>One cycle with its full plan, for read-only review of what was trained.</summary>
public sealed class GetTrainingCycleUseCase(
    ITrainingCycleRepository cycles,
    ICurrentUser currentUser)
{
    public async Task<TrainingCycleView> ExecuteAsync(
        Guid cycleId,
        CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();
        var cycle = await cycles.FindForUserAsync(userId, cycleId, cancellationToken)
                    ?? throw new NotFoundException("Cycle", cycleId);

        return CycleMapper.ToView(cycle);
    }
}
