using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Domain.Training;

namespace GriffGym.Application.Exercises;

public sealed record ExerciseView(
    Guid Id,
    string Name,
    ExerciseCategory Category,
    DateTimeOffset CreatedAtUtc,
    DateTimeOffset UpdatedAtUtc,
    int Version,
    long SyncVersion);

/// <summary>One movement as a client asks for it to exist. Idempotent by <paramref name="Id"/>.</summary>
public sealed record ExerciseInput(Guid Id, string Name, ExerciseCategory Category);

public sealed class GetExercisesUseCase(IExerciseRepository exercises, ICurrentUser currentUser)
{
    public async Task<IReadOnlyList<ExerciseView>> ExecuteAsync(CancellationToken cancellationToken)
    {
        var userId = currentUser.RequireUserId();
        var stored = await exercises.ListForUserAsync(userId, cancellationToken);

        return [.. stored.OrderBy(exercise => exercise.Name, StringComparer.Ordinal)
            .Select(ExerciseMapper.ToView)];
    }
}

/// <summary>
/// Brings a lifter's catalogue up to date with a set of movements, without a round trip of its
/// own.
///
/// Creating a cycle carries the exercises its plan refers to, exactly as the Android generator
/// carries <c>requiredExercises</c> on the plan: persisting a program must never depend on the
/// catalogue having been seeded first.
/// </summary>
public sealed class SynchroniseExercisesUseCase(
    IExerciseRepository exercises,
    IIdentifierFactory identifiers,
    IClock clock)
{
    public async Task<IReadOnlyList<Exercise>> ExecuteAsync(
        Guid userId,
        IReadOnlyList<ExerciseInput> inputs,
        CancellationToken cancellationToken)
    {
        if (inputs.Count == 0)
        {
            return [];
        }

        var now = clock.UtcNow;
        var requested = inputs
            .Select(input => input with { Id = input.Id == Guid.Empty ? identifiers.NewId() : input.Id })
            .GroupBy(input => input.Id)
            .Select(group => group.Last())
            .ToList();

        var existing = (await exercises.ListForUserAsync(
                userId,
                [.. requested.Select(input => input.Id)],
                cancellationToken))
            .ToDictionary(exercise => exercise.Id);

        var result = new List<Exercise>(requested.Count);

        foreach (var input in requested)
        {
            if (existing.TryGetValue(input.Id, out var stored))
            {
                stored.Rename(input.Name, input.Category, now);
                result.Add(stored);
                continue;
            }

            var created = Exercise.Create(input.Id, userId, input.Name, input.Category, now);
            exercises.Add(created);
            result.Add(created);
        }

        return result;
    }
}

internal static class ExerciseMapper
{
    public static ExerciseView ToView(Exercise exercise) => new(
        exercise.Id,
        exercise.Name,
        exercise.Category,
        exercise.CreatedAtUtc,
        exercise.UpdatedAtUtc,
        exercise.Version,
        exercise.SyncVersion);
}
