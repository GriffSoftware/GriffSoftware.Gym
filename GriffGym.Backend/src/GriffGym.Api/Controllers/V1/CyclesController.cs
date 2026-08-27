using GriffGym.Api.Contracts.V1;
using GriffGym.Api.Mapping;
using GriffGym.Application.Cycles;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace GriffGym.Api.Controllers.V1;

[ApiController]
[Route($"{ApiRoutes.Base}/cycles")]
[Authorize]
[Produces("application/json")]
public sealed class CyclesController : ControllerBase
{
    /// <summary>
    /// Every cycle with its week-by-week progress, newest first.
    ///
    /// Summaries, without the full plan: a cycles screen draws a list, and shipping six weeks of
    /// prescribed sets for each entry to render a progress bar would be absurd.
    /// </summary>
    [HttpGet]
    [ProducesResponseType<IReadOnlyList<CycleSummaryResponse>>(StatusCodes.Status200OK)]
    public async Task<ActionResult<IReadOnlyList<CycleSummaryResponse>>> List(
        [FromServices] GetTrainingCyclesUseCase useCase,
        CancellationToken cancellationToken)
    {
        var cycles = await useCase.ExecuteAsync(cancellationToken);

        return Ok(cycles.Select(TrainingMapping.ToResponse).ToList());
    }

    /// <summary>One cycle with the full plan it was trained on.</summary>
    [HttpGet("{cycleId:guid}")]
    [ProducesResponseType<CycleResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<ActionResult<CycleResponse>> Get(
        Guid cycleId,
        [FromServices] GetTrainingCycleUseCase useCase,
        CancellationToken cancellationToken)
    {
        var cycle = await useCase.ExecuteAsync(cycleId, cancellationToken);

        return Ok(cycle.ToResponse());
    }

    /// <summary>
    /// Starts a cycle: the cycle, its exercises, its program, six weeks, their workouts and every
    /// prescribed set — one transaction or none of it.
    ///
    /// Idempotent by identifier. A phone that retried after a timeout gets 200 and the cycle it
    /// already created, not 201 and a second copy of the same six weeks.
    /// </summary>
    [HttpPost]
    [ProducesResponseType<CycleResponse>(StatusCodes.Status201Created)]
    [ProducesResponseType<CycleResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    public async Task<ActionResult<CycleResponse>> Create(
        [FromBody] CreateCycleRequest request,
        [FromServices] CreateTrainingCycleUseCase useCase,
        CancellationToken cancellationToken)
    {
        var result = await useCase.ExecuteAsync(request.ToCommand(), cancellationToken);
        var response = result.Cycle.ToResponse();

        return result.WasCreated
            ? Created($"/{ApiRoutes.Base}/cycles/{response.Id}", response)
            : Ok(response);
    }

    /// <summary>Closes a cycle once its last scheduled unit has been trained.</summary>
    [HttpPost("{cycleId:guid}/complete")]
    [ProducesResponseType<CycleResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status422UnprocessableEntity)]
    public async Task<ActionResult<CycleResponse>> Complete(
        Guid cycleId,
        [FromBody] CompleteCycleRequest? request,
        [FromServices] CompleteTrainingCycleUseCase useCase,
        CancellationToken cancellationToken)
    {
        var cycle = await useCase.ExecuteAsync(
            new CompleteTrainingCycleCommand(cycleId, request?.CompletedAtUtc),
            cancellationToken);

        return Ok(cycle.ToResponse());
    }

    /// <summary>
    /// Moves the plan's pointer to the next unit to train.
    ///
    /// A pointer rather than a date because the plan is a sequence, not a calendar: training a
    /// day early or a week late makes no difference to what comes next.
    /// </summary>
    [HttpPut("{cycleId:guid}/progress")]
    [ProducesResponseType<CycleResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status422UnprocessableEntity)]
    public async Task<ActionResult<CycleResponse>> UpdateProgress(
        Guid cycleId,
        [FromBody] UpdateCycleProgressRequest request,
        [FromServices] UpdateCycleProgressUseCase useCase,
        CancellationToken cancellationToken)
    {
        var cycle = await useCase.ExecuteAsync(
            new UpdateCycleProgressCommand(cycleId, request.CurrentWorkoutTemplateId),
            cancellationToken);

        return Ok(cycle.ToResponse());
    }
}
