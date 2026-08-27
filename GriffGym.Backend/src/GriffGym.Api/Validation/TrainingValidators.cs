using FluentValidation;
using GriffGym.Api.Contracts.V1;
using GriffGym.Domain.Training;

namespace GriffGym.Api.Validation;

/// <summary>
/// The training rules that every payload carrying loads and intensities has to satisfy.
///
/// These duplicate checks the domain also makes, on purpose: the domain protects its own
/// invariants whatever calls it, while these turn a bad request into a 400 with a field name
/// instead of a 422 with one sentence.
/// </summary>
internal static class TrainingRules
{
    public static IRuleBuilderOptions<T, decimal?> ValidWeight<T>(
        this IRuleBuilder<T, decimal?> rule) =>
        rule.Must(value => value is null || Weight.IsValid(value.Value))
            .WithMessage($"'{{PropertyName}}' must be between 0 and {Weight.MaxKilograms} kg.");

    public static IRuleBuilderOptions<T, decimal> ValidWeight<T>(
        this IRuleBuilder<T, decimal> rule) =>
        rule.Must(Weight.IsValid)
            .WithMessage($"'{{PropertyName}}' must be between 0 and {Weight.MaxKilograms} kg.");

    public static IRuleBuilderOptions<T, decimal?> ValidRpe<T>(this IRuleBuilder<T, decimal?> rule) =>
        rule.Must(value => value is null || Rpe.IsValid(value.Value))
            .WithMessage(
                $"'{{PropertyName}}' must be between {Rpe.MinValue} and {Rpe.MaxValue} in steps of {Rpe.Step}.");
}

public sealed class UpdateReferenceMaxRequestValidator : AbstractValidator<UpdateReferenceMaxRequest>
{
    public UpdateReferenceMaxRequestValidator() =>
        RuleFor(request => request.ValueKg)
            .ValidWeight()
            .GreaterThan(0)
            .WithMessage("A reference max must be above zero — a cycle cannot be planned from nothing.");
}

public sealed class ExerciseRequestValidator : AbstractValidator<ExerciseRequest>
{
    public ExerciseRequestValidator()
    {
        RuleFor(request => request.Id).NotEmpty();
        RuleFor(request => request.Name).NotEmpty().MaximumLength(Exercise.MaxNameLength);
        RuleFor(request => request.Category).IsInEnum();
    }
}

public sealed class PlannedSetRequestValidator : AbstractValidator<PlannedSetRequest>
{
    public PlannedSetRequestValidator()
    {
        RuleFor(request => request.Position).GreaterThanOrEqualTo(1);
        RuleFor(request => request.WeightKg).ValidWeight();
        RuleFor(request => request.Reps).GreaterThanOrEqualTo(1).When(request => request.Reps is not null);
        RuleFor(request => request.RpeMin).ValidRpe();
        RuleFor(request => request.RpeMax).ValidRpe();

        RuleFor(request => request)
            .Must(request => (request.RpeMin is null) == (request.RpeMax is null))
            .WithName("targetRpe")
            .WithMessage("An RPE target needs both bounds or neither.");

        RuleFor(request => request)
            .Must(request => request.RpeMin is null || request.RpeMax is null || request.RpeMin <= request.RpeMax)
            .WithName("targetRpe")
            .WithMessage("An RPE target range cannot be inverted.");
    }
}

public sealed class ExerciseTemplateRequestValidator : AbstractValidator<ExerciseTemplateRequest>
{
    public ExerciseTemplateRequestValidator()
    {
        RuleFor(request => request.Position).GreaterThanOrEqualTo(1);
        RuleFor(request => request.ExerciseId).NotEmpty();
        RuleFor(request => request.ExerciseName).MaximumLength(Exercise.MaxNameLength);
        RuleFor(request => request.Type).IsInEnum();
        RuleFor(request => request.PlannedSets).NotEmpty();
        RuleForEach(request => request.PlannedSets).SetValidator(new PlannedSetRequestValidator());
    }
}

public sealed class WorkoutTemplateRequestValidator : AbstractValidator<WorkoutTemplateRequest>
{
    public WorkoutTemplateRequestValidator()
    {
        RuleFor(request => request.DayNumber).GreaterThanOrEqualTo(1);
        RuleFor(request => request.SequenceNumber).GreaterThanOrEqualTo(1);
        RuleFor(request => request.Title).NotEmpty().MaximumLength(200);
        RuleFor(request => request.Exercises).NotEmpty();
        RuleForEach(request => request.Exercises).SetValidator(new ExerciseTemplateRequestValidator());
    }
}

public sealed class WeekRequestValidator : AbstractValidator<WeekRequest>
{
    public WeekRequestValidator()
    {
        RuleFor(request => request.WeekNumber).GreaterThanOrEqualTo(1);
        RuleFor(request => request.Label).NotEmpty().MaximumLength(64);
        RuleFor(request => request.Type).IsInEnum();
        RuleFor(request => request.Workouts).NotEmpty();
        RuleForEach(request => request.Workouts).SetValidator(new WorkoutTemplateRequestValidator());
    }
}

public sealed class ProgramRequestValidator : AbstractValidator<ProgramRequest>
{
    public ProgramRequestValidator()
    {
        RuleFor(request => request.Name).NotEmpty().MaximumLength(TrainingProgram.MaxNameLength);
        RuleFor(request => request.Weeks).NotEmpty();
        RuleForEach(request => request.Weeks).SetValidator(new WeekRequestValidator());

        RuleFor(request => request.Weeks)
            .Must(weeks => weeks.Select(week => week.WeekNumber).Distinct().Count() == weeks.Count)
            .WithMessage("Two weeks share the same number.");
    }
}

public sealed class CreateCycleRequestValidator : AbstractValidator<CreateCycleRequest>
{
    public CreateCycleRequestValidator()
    {
        RuleFor(request => request.CycleNumber).GreaterThanOrEqualTo(1);

        RuleFor(request => request.SquatReferenceMaxKg).ValidWeight().GreaterThan(0);
        RuleFor(request => request.BenchPressReferenceMaxKg).ValidWeight().GreaterThan(0);
        RuleFor(request => request.DeadliftReferenceMaxKg).ValidWeight().GreaterThan(0);

        RuleFor(request => request.StartedAtUtc).NotEmpty();

        RuleForEach(request => request.Exercises).SetValidator(new ExerciseRequestValidator());

        RuleFor(request => request.Program).NotNull().SetValidator(new ProgramRequestValidator());
    }
}

public sealed class CompleteCycleRequestValidator : AbstractValidator<CompleteCycleRequest>
{
    public CompleteCycleRequestValidator() =>
        RuleFor(request => request.CompletedAtUtc)
            .Must(value => value is null || value.Value <= DateTimeOffset.UtcNow.AddDays(1))
            .WithMessage("A cycle cannot be completed in the future.");
}
