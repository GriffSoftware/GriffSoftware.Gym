using GriffGym.Api.Contracts.V1;
using GriffGym.Api.Mapping;
using GriffGym.Application.Common;
using GriffGym.Application.Workouts;
using GriffGym.Domain.Training;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace GriffGym.Api.Controllers.V1;

[ApiController]
[Route($"{ApiRoutes.Base}/workouts")]
[Authorize]
[Produces("application/json")]
public sealed class WorkoutsController : ControllerBase
{
    /// <summary>
    /// Paginated history, newest first.
    ///
    /// Never unbounded: several years in, a lifter has hundreds of sessions and tens of thousands
    /// of sets. The cycle, status and date filters are here because a history screen needs them,
    /// and because adding them later would have meant a second endpoint.
    /// </summary>
    [HttpGet]
    [ProducesResponseType<PagedResponse<WorkoutSummaryResponse>>(StatusCodes.Status200OK)]
    public async Task<ActionResult<PagedResponse<WorkoutSummaryResponse>>> List(
        [FromServices] GetWorkoutHistoryUseCase useCase,
        CancellationToken cancellationToken,
        [FromQuery] int page = 1,
        [FromQuery] int pageSize = PageRequest.DefaultPageSize,
        [FromQuery] Guid? cycleId = null,
        [FromQuery] WorkoutSessionStatus? status = null,
        [FromQuery] DateOnly? from = null,
        [FromQuery] DateOnly? to = null)
    {
        var history = await useCase.ExecuteAsync(
            new WorkoutHistoryQuery(page, pageSize, cycleId, status, from, to),
            cancellationToken);

        return Ok(history.ToResponse(WorkoutMapping.ToResponse));
    }

    /// <summary>
    /// The workout the lifter is in the middle of.
    ///
    /// 204 when there is none — an empty body says "nothing running" more honestly than a 404,
    /// which would suggest the endpoint itself was wrong.
    ///
    /// Routed before <c>{sessionId:guid}</c> and matched by literal, so "active" can never be
    /// mistaken for an identifier.
    /// </summary>
    [HttpGet("active")]
    [ProducesResponseType<WorkoutResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    public async Task<ActionResult<WorkoutResponse>> Active(
        [FromServices] GetActiveWorkoutUseCase useCase,
        CancellationToken cancellationToken)
    {
        var session = await useCase.ExecuteAsync(cancellationToken);

        return session is null ? NoContent() : Ok(session.ToResponse());
    }

    [HttpGet("{sessionId:guid}")]
    [ProducesResponseType<WorkoutResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<ActionResult<WorkoutResponse>> Get(
        Guid sessionId,
        [FromServices] GetWorkoutSessionUseCase useCase,
        CancellationToken cancellationToken)
    {
        var session = await useCase.ExecuteAsync(sessionId, cancellationToken);

        return Ok(session.ToResponse());
    }

    /// <summary>
    /// Starts a workout, or uploads one the client already holds.
    ///
    /// Idempotent by identifier, like cycle creation: a retried POST returns the session that
    /// exists rather than logging the same workout twice.
    /// </summary>
    [HttpPost]
    [ProducesResponseType<WorkoutResponse>(StatusCodes.Status201Created)]
    [ProducesResponseType<WorkoutResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    public async Task<ActionResult<WorkoutResponse>> Create(
        [FromBody] CreateWorkoutRequest request,
        [FromServices] CreateWorkoutSessionUseCase useCase,
        CancellationToken cancellationToken)
    {
        var result = await useCase.ExecuteAsync(request.ToCommand(), cancellationToken);
        var response = result.Session.ToResponse();

        return result.WasCreated
            ? Created($"/{ApiRoutes.Base}/workouts/{response.Id}", response)
            : Ok(response);
    }

    /// <summary>
    /// Replaces the mutable part of a live session with what the client holds.
    ///
    /// The offline-first upload path: sending the tree wholesale is idempotent and survives a
    /// lost intermediate request, which matters more than bandwidth for a phone that spent the
    /// session with no signal.
    /// </summary>
    [HttpPut("{sessionId:guid}")]
    [ProducesResponseType<WorkoutResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    [ProducesResponseType(StatusCodes.Status422UnprocessableEntity)]
    public async Task<ActionResult<WorkoutResponse>> Update(
        Guid sessionId,
        [FromBody] UpdateWorkoutRequest request,
        [FromServices] UpdateWorkoutSessionUseCase useCase,
        CancellationToken cancellationToken)
    {
        var session = await useCase.ExecuteAsync(request.ToCommand(sessionId), cancellationToken);

        return Ok(session.ToResponse());
    }

    /// <summary>
    /// Writes one set the moment the lifter finishes it — the hot path of the whole product.
    ///
    /// A PUT on the set rather than a PATCH on the session, because a set is a thing with an
    /// identity the client already knows, and writing it twice must be the same as writing it
    /// once.
    /// </summary>
    [HttpPut("{sessionId:guid}/sets/{setId:guid}")]
    [ProducesResponseType<WorkoutResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    [ProducesResponseType(StatusCodes.Status422UnprocessableEntity)]
    public async Task<ActionResult<WorkoutResponse>> LogSet(
        Guid sessionId,
        Guid setId,
        [FromBody] LogSetRequest request,
        [FromServices] LogSetUseCase useCase,
        CancellationToken cancellationToken)
    {
        var session = await useCase.ExecuteAsync(
            request.ToCommand(sessionId, setId),
            cancellationToken);

        return Ok(session.ToResponse());
    }

    [HttpPost("{sessionId:guid}/complete")]
    [ProducesResponseType<WorkoutResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    [ProducesResponseType(StatusCodes.Status422UnprocessableEntity)]
    public async Task<ActionResult<WorkoutResponse>> Complete(
        Guid sessionId,
        [FromBody] FinishWorkoutRequest? request,
        [FromServices] CompleteWorkoutSessionUseCase useCase,
        CancellationToken cancellationToken)
    {
        var session = await useCase.ExecuteAsync(request.ToCommand(sessionId), cancellationToken);

        return Ok(session.ToResponse());
    }

    [HttpPost("{sessionId:guid}/cancel")]
    [ProducesResponseType<WorkoutResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    [ProducesResponseType(StatusCodes.Status422UnprocessableEntity)]
    public async Task<ActionResult<WorkoutResponse>> Cancel(
        Guid sessionId,
        [FromBody] FinishWorkoutRequest? request,
        [FromServices] CancelWorkoutSessionUseCase useCase,
        CancellationToken cancellationToken)
    {
        var session = await useCase.ExecuteAsync(request.ToCommand(sessionId), cancellationToken);

        return Ok(session.ToResponse());
    }
}
