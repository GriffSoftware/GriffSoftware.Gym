using FluentValidation;
using Microsoft.AspNetCore.Mvc.Filters;

namespace GriffGym.Api.Validation;

/// <summary>
/// Runs the FluentValidation validator for every action argument that has one.
///
/// One filter rather than a <c>validator.ValidateAndThrow</c> at the top of each action: a
/// controller that forgets the call is an endpoint with no validation at all, and that is not
/// the sort of mistake that shows up in review.
///
/// Failures are thrown, not returned, so that <see cref="Errors.GlobalExceptionHandler"/>
/// renders them in the same ProblemDetails shape as every other error the API produces.
/// </summary>
public sealed class ValidationActionFilter(IServiceProvider services) : IAsyncActionFilter
{
    public async Task OnActionExecutionAsync(
        ActionExecutingContext context,
        ActionExecutionDelegate next)
    {
        foreach (var argument in context.ActionArguments.Values)
        {
            if (argument is null)
            {
                continue;
            }

            var validatorType = typeof(IValidator<>).MakeGenericType(argument.GetType());

            if (services.GetService(validatorType) is not IValidator validator)
            {
                continue;
            }

            var result = await validator.ValidateAsync(
                new ValidationContext<object>(argument),
                context.HttpContext.RequestAborted);

            if (!result.IsValid)
            {
                throw new ValidationException(result.Errors);
            }
        }

        await next();
    }
}
