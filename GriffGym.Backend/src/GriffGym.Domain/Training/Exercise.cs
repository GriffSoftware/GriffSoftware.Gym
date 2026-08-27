using GriffGym.Domain.Common;

namespace GriffGym.Domain.Training;

/// <summary>
/// A movement in one lifter's catalogue.
///
/// The catalogue is per user rather than global: the phone owns it, invents the identifiers,
/// and a lifter who renames "Ławka" to "Bench" must not rename it for anybody else. History
/// never depends on this row anyway — every log and template carries its own name snapshot.
/// </summary>
public sealed class Exercise : Entity
{
    public const int MaxNameLength = 120;

    private Exercise(
        Guid id,
        Guid userId,
        string name,
        ExerciseCategory category,
        DateTimeOffset createdAtUtc,
        DateTimeOffset updatedAtUtc)
        : base(id, createdAtUtc, updatedAtUtc)
    {
        DomainException.Require(userId != Guid.Empty, "An exercise must belong to a user.");
        DomainException.Require(!string.IsNullOrWhiteSpace(name), "An exercise needs a name.");
        DomainException.Require(
            name.Trim().Length <= MaxNameLength,
            $"An exercise name must be at most {MaxNameLength} characters.");

        UserId = userId;
        Name = name.Trim();
        Category = category;
    }

    public Guid UserId { get; }

    public string Name { get; private set; }

    public ExerciseCategory Category { get; private set; }

    public bool IsBigThree => Category != ExerciseCategory.Accessory;

    public static Exercise Create(
        Guid id,
        Guid userId,
        string name,
        ExerciseCategory category,
        DateTimeOffset now) =>
        new(id, userId, name, category, now, now);

    public static Exercise FromStorage(
        Guid id,
        Guid userId,
        string name,
        ExerciseCategory category,
        DateTimeOffset createdAtUtc,
        DateTimeOffset updatedAtUtc) =>
        new(id, userId, name, category, createdAtUtc, updatedAtUtc);

    public void Rename(string name, ExerciseCategory category, DateTimeOffset now)
    {
        var trimmed = (name ?? string.Empty).Trim();

        DomainException.Require(trimmed.Length > 0, "An exercise needs a name.");
        DomainException.Require(
            trimmed.Length <= MaxNameLength,
            $"An exercise name must be at most {MaxNameLength} characters.");

        if (Name == trimmed && Category == category)
        {
            return;
        }

        Name = trimmed;
        Category = category;
        Touch(now);
    }
}
