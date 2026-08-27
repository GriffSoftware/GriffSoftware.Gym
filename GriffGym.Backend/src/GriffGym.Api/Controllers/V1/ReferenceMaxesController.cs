using GriffGym.Api.Contracts.V1;
using GriffGym.Api.Mapping;
using GriffGym.Application.ReferenceMaxes;
using GriffGym.Domain.Training;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace GriffGym.Api.Controllers.V1;

[ApiController]
[Route($"{ApiRoutes.Base}/reference-maxes")]
[Authorize]
[Produces("application/json")]
public sealed class ReferenceMaxesController : ControllerBase
{
    [HttpGet]
    [ProducesResponseType<IReadOnlyList<ReferenceMaxResponse>>(StatusCodes.Status200OK)]
    public async Task<ActionResult<IReadOnlyList<ReferenceMaxResponse>>> List(
        [FromServices] GetReferenceMaxesUseCase useCase,
        CancellationToken cancellationToken)
    {
        var maxes = await useCase.ExecuteAsync(cancellationToken);

        return Ok(maxes.Select(TrainingMapping.ToResponse).ToList());
    }

    /// <summary>
    /// Sets one planning number, creating it on first use.
    ///
    /// A PUT because there is exactly one squat max per lifter and sending the same value twice
    /// must leave the same single row behind. Changing it does not touch cycles already planned:
    /// each of those keeps the snapshot it was built from.
    /// </summary>
    [HttpPut("{lift}")]
    [ProducesResponseType<ReferenceMaxResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    public async Task<ActionResult<ReferenceMaxResponse>> Update(
        LiftType lift,
        [FromBody] UpdateReferenceMaxRequest request,
        [FromServices] UpdateReferenceMaxUseCase useCase,
        CancellationToken cancellationToken)
    {
        var updated = await useCase.ExecuteAsync(request.ToCommand(lift), cancellationToken);

        return Ok(updated.ToResponse());
    }
}

[ApiController]
[Route($"{ApiRoutes.Base}/exercises")]
[Authorize]
[Produces("application/json")]
public sealed class ExercisesController : ControllerBase
{
    /// <summary>
    /// The lifter's movement catalogue.
    ///
    /// Read-only here: entries are created as a side effect of uploading a cycle, which carries
    /// the movements its plan refers to so that persisting a program never depends on the
    /// catalogue having been seeded first.
    /// </summary>
    [HttpGet]
    [ProducesResponseType<IReadOnlyList<ExerciseResponse>>(StatusCodes.Status200OK)]
    public async Task<ActionResult<IReadOnlyList<ExerciseResponse>>> List(
        [FromServices] GriffGym.Application.Exercises.GetExercisesUseCase useCase,
        CancellationToken cancellationToken)
    {
        var exercises = await useCase.ExecuteAsync(cancellationToken);

        return Ok(exercises.Select(TrainingMapping.ToResponse).ToList());
    }
}
