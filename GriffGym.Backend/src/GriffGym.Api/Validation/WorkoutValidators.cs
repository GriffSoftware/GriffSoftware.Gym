using FluentValidation;
using GriffGym.Api.Contracts.V1;
using GriffGym.Domain.Training;
using GriffGym.Domain.Workouts;

namespace GriffGym.Api.Validation;

public sealed class SetLogRequestValidator : AbstractValidator<SetLogRequest>
{
    public SetLogRequestValidator()
    {
        RuleFor(request => request.Position).GreaterThanOrEqualTo(1);

        RuleFor(request => request.PlannedWeightKg).ValidWeight();
        RuleFor(request => request.PlannedReps)
            .GreaterThanOrEqualTo(1)
            .When(request => request.PlannedReps is not null);
        RuleFor(request => request.PlannedRpeMin).ValidRpe();
        RuleFor(request => request.PlannedRpeMax).ValidRpe();

        RuleFor(request => request.ActualWeightKg).ValidWeight();
        RuleFor(request => request.ActualReps)
            .GreaterThanOrEqualTo(0)
            .When(request => request.ActualReps is not null);
        RuleFor(request => request.ActualRpe).ValidRpe();

        RuleFor(request => request.Notes).MaximumLength(SetLog.MaxNotesLength);

        // A set cannot be ticked off without saying what was lifted, or "completed" starts to
        // mean "completed, contents unknown" in a training log that is meant to last years.
        RuleFor(request => request)
            .Must(request =>
                !request.Completed || (request.ActualWeightKg is not null && request.ActualReps >= 1))
            .WithName("completed")
            .WithMessage("A completed set must record both a weight and at least one rep.");
    }
}

public sealed class ExerciseLogRequestValidator : AbstractValidator<ExerciseLogRequest>
{
    public ExerciseLogRequestValidator()
    {
        RuleFor(request => request.Position).GreaterThanOrEqualTo(1);
        RuleFor(request => request.ExerciseName).MaximumLength(Exercise.MaxNameLength);
        RuleFor(request => request.Type).IsInEnum();
        RuleFor(request => request.Notes).MaximumLength(ExerciseLog.MaxNotesLength);
        RuleForEach(request => request.Sets).SetValidator(new SetLogRequestValidator());
    }
}

public sealed class CreateWorkoutRequestValidator : AbstractValidator<CreateWorkoutRequest>
{
    public CreateWorkoutRequestValidator()
    {
        RuleFor(request => request.Status).IsInEnum().When(request => request.Status is not null);
        RuleFor(request => request.WeekNumber)
            .GreaterThanOrEqualTo(1)
            .When(request => request.WeekNumber is not null);
        RuleFor(request => request.DayNumber)
            .GreaterThanOrEqualTo(1)
            .When(request => request.DayNumber is not null);
        RuleFor(request => request.Title).MaximumLength(WorkoutSession.MaxTitleLength);
        RuleFor(request => request.Notes).MaximumLength(WorkoutSession.MaxNotesLength);

        RuleForEach(request => request.Exercises).SetValidator(new ExerciseLogRequestValidator());

        // Either snapshot a planned unit, or bring your own contents. Neither means there is
        // nothing to log.
        RuleFor(request => request)
            .Must(request =>
                request.Exercises is { Count: > 0 }
                || (request.TrainingCycleId is not null && request.WorkoutTemplateId is not null))
            .WithName("workout")
            .WithMessage(
                "Send trainingCycleId and workoutTemplateId to start a planned workout, or exercises to upload one.");

        RuleFor(request => request)
            .Must(request =>
                request.StartedAtUtc is null
                || request.FinishedAtUtc is null
                || request.FinishedAtUtc >= request.StartedAtUtc)
            .WithName("finishedAtUtc")
            .WithMessage("A workout cannot finish before it started.");
    }
}

public sealed class UpdateWorkoutRequestValidator : AbstractValidator<UpdateWorkoutRequest>
{
    public UpdateWorkoutRequestValidator()
    {
        RuleFor(request => request.ExpectedVersion)
            .GreaterThanOrEqualTo(1)
            .When(request => request.ExpectedVersion is not null);
        RuleFor(request => request.Notes).MaximumLength(WorkoutSession.MaxNotesLength);
        RuleForEach(request => request.Exercises).SetValidator(new ExerciseLogRequestValidator());
    }
}

public sealed class LogSetRequestValidator : AbstractValidator<LogSetRequest>
{
    public LogSetRequestValidator()
    {
        RuleFor(request => request.ExpectedVersion)
            .GreaterThanOrEqualTo(1)
            .When(request => request.ExpectedVersion is not null);
        RuleFor(request => request.WeightKg).ValidWeight();
        RuleFor(request => request.Reps)
            .GreaterThanOrEqualTo(0)
            .When(request => request.Reps is not null);
        RuleFor(request => request.Rpe).ValidRpe();
        RuleFor(request => request.Notes).MaximumLength(SetLog.MaxNotesLength);

        RuleFor(request => request)
            .Must(request => !request.Completed || (request.WeightKg is not null && request.Reps >= 1))
            .WithName("completed")
            .WithMessage("A completed set must record both a weight and at least one rep.");
    }
}

public sealed class FinishWorkoutRequestValidator : AbstractValidator<FinishWorkoutRequest>
{
    public FinishWorkoutRequestValidator() =>
        RuleFor(request => request.ExpectedVersion)
            .GreaterThanOrEqualTo(1)
            .When(request => request.ExpectedVersion is not null);
}
