using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Domain.Training;
using Microsoft.Extensions.Logging;

namespace GriffGym.Application.ReferenceMaxes;

public sealed record ReferenceMaxView(
    Guid Id,
    LiftType Lift,
    decimal ValueKg,
    DateTimeOffset CreatedAtUtc,
    DateTimeOffset UpdatedAtUtc,
    int Version,
    long SyncVersion);

/// <summary>
/// The lift is taken from the route, never from the body, so a request cannot claim to update
/// the squat while carrying a bench payload.
/// </summary>
public sealed record UpdateReferenceMaxCommand(LiftType Lift, decimal ValueKg, Guid? Id);

public sealed class GetReferenceMaxesUseCase(
    IReferenceMaxRepository referenceMaxes,
    ICurrentUser currentUser)
{
    public async Task<IReadOnlyList<ReferenceMaxView>> ExecuteAsync(
        CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();
        var stored = await referenceMaxes.ListForUserAsync(userId, cancellationToken);

        return [.. stored.OrderBy(max => max.Lift).Select(ReferenceMaxMapper.ToView)];
    }
}

/// <summary>
/// Sets one planning number, creating it on first use.
///
/// A PUT rather than a POST because there is exactly one squat max per lifter, and sending the
/// same value twice must leave the same single row behind. Historical cycles keep the snapshot
/// they were built from; this deliberately does not reach back into them.
/// </summary>
public sealed class UpdateReferenceMaxUseCase(
    IReferenceMaxRepository referenceMaxes,
    IUnitOfWork unitOfWork,
    ICurrentUser currentUser,
    IIdentifierFactory identifiers,
    IClock clock,
    ILogger<UpdateReferenceMaxUseCase> logger)
{
    public async Task<ReferenceMaxView> ExecuteAsync(
        UpdateReferenceMaxCommand command,
        CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();
        var now = clock.UtcNow;
        var weight = Weight.Of(command.ValueKg);

        var existing = await referenceMaxes.FindForUserAsync(userId, command.Lift, cancellationToken);

        if (existing is null)
        {
            // The client may supply the id it already generated locally, so that the row it
            // created offline and the row the server creates are the same row.
            existing = ReferenceMax.Create(
                command.Id ?? identifiers.NewId(),
                userId,
                command.Lift,
                weight,
                now);

            referenceMaxes.Add(existing);
        }
        else
        {
            existing.UpdateValue(weight, now);
        }

        await unitOfWork.SaveChangesAsync(cancellationToken);

        logger.LogInformation("Reference max updated for {UserId} {Lift}", userId, command.Lift);

        return ReferenceMaxMapper.ToView(existing);
    }
}

internal static class ReferenceMaxMapper
{
    public static ReferenceMaxView ToView(ReferenceMax max) => new(
        max.Id,
        max.Lift,
        max.Value.Kilograms,
        max.CreatedAtUtc,
        max.UpdatedAtUtc,
        max.Version,
        max.SyncVersion);
}
