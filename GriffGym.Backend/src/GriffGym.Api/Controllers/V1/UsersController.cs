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
}
