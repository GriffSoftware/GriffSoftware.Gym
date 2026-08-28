using GriffGym.Api.Contracts.V1;
using GriffGym.Api.Mapping;
using GriffGym.Application.Users;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace GriffGym.Api.Controllers.V1;

[ApiController]
[Route($"{ApiRoutes.Base}/users")]
[Authorize]
[Produces("application/json")]
public sealed class UsersController : ControllerBase
{
    /// <summary>
    /// The signed-in lifter's own profile.
    ///
    /// There is deliberately no <c>GET /users/{id}</c>. One account has no business reading
    /// another, so the endpoint that would allow it does not exist rather than being guarded.
    /// </summary>
    [HttpGet("me")]
    [ProducesResponseType<UserResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<ActionResult<UserResponse>> Me(
        [FromServices] GetCurrentUserUseCase useCase,
        CancellationToken cancellationToken)
    {
        var profile = await useCase.ExecuteAsync(cancellationToken);

        return Ok(profile.ToResponse());
    }

    /// <summary>
    /// Permanently deletes the signed-in lifter's account and everything it owns.
    ///
    /// No user id in the route and none in a body: which account goes is read from the access
    /// token's subject, so this endpoint cannot be pointed at anybody else.
    ///
    /// <c>204</c> and nothing else. Returning the deleted account would be handing back a copy
    /// of the thing the request asked to destroy, and there is no representation of a resource
    /// that no longer exists worth sending.
    ///
    /// Repeating the request is safe. The refresh tokens are gone and the access token stops
    /// being accepted the moment the account row does, so a second attempt is answered
    /// <c>401</c> rather than doing anything a second time.
    /// </summary>
    [HttpDelete("me")]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> DeleteMe(
        [FromServices] DeleteCurrentUserAccountUseCase useCase,
        [FromServices] ILogger<UsersController> logger,
        CancellationToken cancellationToken)
    {
        var summary = await useCase.ExecuteAsync(cancellationToken);

        // Worth a line in the log: it is the one operation in this API that destroys data on
        // purpose, and the counts are the only remaining evidence of what was there.
        logger.LogInformation(
            "Deleted account: {Sessions} workout sessions, {Cycles} cycles, {Exercises} " +
            "exercises, {ReferenceMaxes} reference maxes, {RefreshTokens} refresh tokens",
            summary.WorkoutSessions,
            summary.TrainingCycles,
            summary.Exercises,
            summary.ReferenceMaxes,
            summary.RefreshTokens);

        return NoContent();
    }
}
