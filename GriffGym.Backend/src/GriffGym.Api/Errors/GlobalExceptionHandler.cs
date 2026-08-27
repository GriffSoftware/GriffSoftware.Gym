using FluentValidation;
using GriffGym.Application.Common;
using GriffGym.Domain.Common;
using Microsoft.AspNetCore.Diagnostics;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Infrastructure;

namespace GriffGym.Api.Errors;

/// <summary>
/// Turns every exception the application can raise into one consistent RFC 9457 problem
/// document.
///
/// One handler rather than a try/catch in each controller. A per-controller catch that returns
/// <c>BadRequest(ex.Message)</c> is how internal detail — table names, connection strings, stack
/// frames — ends up in a mobile client, and how the same failure ends up with three different
/// status codes depending on which endpoint hit it.
///
/// Anything not recognised here is a 500 with a fixed sentence. The detail goes to the log,
/// where the operator can see it, and nowhere near the response.
/// </summary>
internal sealed class GlobalExceptionHandler(
    IProblemDetailsService problemDetails,
    ILogger<GlobalExceptionHandler> logger) : IExceptionHandler
{
    public async ValueTask<bool> TryHandleAsync(
        HttpContext httpContext,
        Exception exception,
        CancellationToken cancellationToken)
    {
        var problem = Describe(exception, httpContext);

        httpContext.Response.StatusCode = problem.Status ?? StatusCodes.Status500InternalServerError;

        if (problem.Status >= StatusCodes.Status500InternalServerError)
        {
            logger.LogError(exception, "Unhandled exception on {Method} {Path}",
                httpContext.Request.Method, httpContext.Request.Path);
        }
        else
        {
            logger.LogInformation(
                "Request rejected on {Method} {Path}: {Status} {Title}",
                httpContext.Request.Method,
                httpContext.Request.Path,
                problem.Status,
                problem.Title);
        }

        return await problemDetails.TryWriteAsync(new ProblemDetailsContext
        {
            HttpContext = httpContext,
            Exception = exception,
            ProblemDetails = problem,
        });
    }

    private static ProblemDetails Describe(Exception exception, HttpContext httpContext) =>
        exception switch
        {
            ValidationException validation => Validation(validation, httpContext),

            NotFoundException notFound => Problem(
                StatusCodes.Status404NotFound,
                "Not found",
                notFound.Message,
                httpContext),

            ConcurrencyConflictException concurrency => Concurrency(concurrency, httpContext),

            ConflictException conflict => Problem(
                StatusCodes.Status409Conflict,
                "Conflict",
                conflict.Message,
                httpContext),

            // A well-formed request asking for something the rules forbid — completing a cycle
            // twice, logging a set into a finished workout. Not a 400: nothing about the request
            // was malformed.
            DomainException domain => Problem(
                StatusCodes.Status422UnprocessableEntity,
                "Unprocessable request",
                domain.Message,
                httpContext),

            AuthenticationFailedException or UnauthenticatedException => Problem(
                StatusCodes.Status401Unauthorized,
                "Unauthorized",
                exception.Message,
                httpContext),

            BadHttpRequestException badRequest => Problem(
                StatusCodes.Status400BadRequest,
                "Malformed request",
                badRequest.Message,
                httpContext),

            OperationCanceledException => Problem(
                StatusCodes.Status499ClientClosedRequest,
                "Request cancelled",
                "The client closed the request before it completed.",
                httpContext),

            _ => Problem(
                StatusCodes.Status500InternalServerError,
                "Unexpected error",
                "Something went wrong. The failure has been logged.",
                httpContext),
        };

    private static ValidationProblemDetails Validation(
        ValidationException exception,
        HttpContext httpContext)
    {
        var errors = exception.Errors
            .GroupBy(failure => JsonName(failure.PropertyName))
            .ToDictionary(
                group => group.Key,
                group => group.Select(failure => failure.ErrorMessage).Distinct().ToArray());

        return new ValidationProblemDetails(errors)
        {
            Type = "https://tools.ietf.org/html/rfc9110#section-15.5.1",
            Title = "Validation failed",
            Status = StatusCodes.Status400BadRequest,
            Instance = httpContext.Request.Path,
        };
    }

    private static ProblemDetails Concurrency(
        ConcurrencyConflictException exception,
        HttpContext httpContext)
    {
        var problem = Problem(
            StatusCodes.Status409Conflict,
            "Version conflict",
            exception.Message,
            httpContext);

        // Enough for a client to decide what to do: re-read at the version it was told about,
        // merge, and write again.
        problem.Extensions["expectedVersion"] = exception.ExpectedVersion;
        problem.Extensions["actualVersion"] = exception.ActualVersion;

        return problem;
    }

    private static ProblemDetails Problem(
        int status,
        string title,
        string detail,
        HttpContext httpContext) =>
        new()
        {
            Type = $"https://httpstatuses.io/{status}",
            Title = title,
            Status = status,
            Detail = detail,
            Instance = httpContext.Request.Path,
        };

    /// <summary>
    /// FluentValidation reports <c>Program.Weeks[0].Label</c>; the client sent
    /// <c>program.weeks[0].label</c>. Reporting the name they used is the difference between an
    /// error a mobile developer can act on and one they have to guess at.
    /// </summary>
    private static string JsonName(string propertyName) =>
        string.Join(
            '.',
            propertyName.Split('.').Select(segment =>
                segment.Length == 0 ? segment : char.ToLowerInvariant(segment[0]) + segment[1..]));
}
