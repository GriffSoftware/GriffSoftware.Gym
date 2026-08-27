using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Common;
using Microsoft.Extensions.Logging;

namespace GriffGym.Application.Cycles;

/// <summary>Closes a cycle once its last scheduled unit has been trained.</summary>
public sealed class CompleteTrainingCycleUseCase(
    ITrainingCycleRepository cycles,
    IUnitOfWork unitOfWork,
    ICurrentUser currentUser,
    IClock clock,
    ILogger<CompleteTrainingCycleUseCase> logger)
{
    public async Task<TrainingCycleView> ExecuteAsync(
        CompleteTrainingCycleCommand command,
        CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();
        var now = clock.UtcNow;

        var cycle = await cycles.FindForUserAsync(userId, command.CycleId, cancellationToken)
                    ?? throw new NotFoundException("Cycle", command.CycleId);

        cycle.Complete(command.CompletedAtUtc ?? now, now);
        await unitOfWork.SaveChangesAsync(cancellationToken);

        logger.LogInformation("Cycle completed {CycleId} for {UserId}", cycle.Id, userId);

        return CycleMapper.ToView(cycle);
    }
}

/// <summary>
/// Moves the plan's pointer to the next unit to train.
///
/// The plan is a sequence, not a calendar: training a day early or a week late makes no
/// difference to what comes next, which is why progress is a pointer and not a date.
/// </summary>
public sealed class UpdateCycleProgressUseCase(
    ITrainingCycleRepository cycles,
    IUnitOfWork unitOfWork,
    ICurrentUser currentUser,
    IClock clock)
{
    public async Task<TrainingCycleView> ExecuteAsync(
        UpdateCycleProgressCommand command,
        CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();

        var cycle = await cycles.FindForUserAsync(userId, command.CycleId, cancellationToken)
                    ?? throw new NotFoundException("Cycle", command.CycleId);

        cycle.MoveProgressTo(command.CurrentWorkoutTemplateId, clock.UtcNow);
        await unitOfWork.SaveChangesAsync(cancellationToken);

        return CycleMapper.ToView(cycle);
    }
}
