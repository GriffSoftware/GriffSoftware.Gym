using GriffGym.Api.Contracts.V1;
using GriffGym.Api.Mapping;
using GriffGym.Application.State;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace GriffGym.Api.Controllers.V1;

[ApiController]
[Route($"{ApiRoutes.Base}/state")]
[Authorize]
[Produces("application/json")]
public sealed class StateController : ControllerBase
{
    /// <summary>
    /// Everything this lifter's installation is made of, in one read-only document.
    ///
    /// The answer to "my phone is in a river": a fresh install signs in, asks once, and has what
    /// it needs to rebuild its local database exactly — planning numbers, every cycle with the
    /// plan it was trained on, where the lifter is inside the current plan, every logged session
    /// with its planned and actual sets, and the workout still open.
    ///
    /// Read-only and idempotent. Phase 2 adds a <c>since</c> cursor on top of the
    /// <c>syncVersion</c> this returns, so an established account stops re-downloading years of
    /// history it already has.
    /// </summary>
    [HttpGet]
    [ProducesResponseType<ApplicationStateResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<ActionResult<ApplicationStateResponse>> Get(
        [FromServices] GetUserApplicationStateUseCase useCase,
        CancellationToken cancellationToken)
    {
        var state = await useCase.ExecuteAsync(cancellationToken);

        return Ok(state.ToResponse());
    }
}
