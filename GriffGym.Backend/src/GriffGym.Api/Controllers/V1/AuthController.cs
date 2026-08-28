using GriffGym.Api.Contracts.V1;
using GriffGym.Api.Mapping;
using GriffGym.Application.Auth;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;

namespace GriffGym.Api.Controllers.V1;

[ApiController]
[Route($"{ApiRoutes.Base}/auth")]
[Produces("application/json")]
// Credential endpoints are the ones worth grinding at, so they get their own, tighter bucket.
[EnableRateLimiting(RateLimitPolicies.Authentication)]
public sealed class AuthController : ControllerBase
{
    /// <summary>Creates an account and signs it straight in.</summary>
    [HttpPost("register")]
    [AllowAnonymous]
    [ProducesResponseType<AuthenticationResponse>(StatusCodes.Status201Created)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    public async Task<ActionResult<AuthenticationResponse>> Register(
        [FromBody] RegisterRequest request,
        [FromServices] RegisterUserUseCase useCase,
        CancellationToken cancellationToken)
    {
        var result = await useCase.ExecuteAsync(request.ToCommand(), cancellationToken);

        return Created($"/{ApiRoutes.Base}/users/me", result.ToResponse());
    }

    [HttpPost("login")]
    [AllowAnonymous]
    [ProducesResponseType<AuthenticationResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<ActionResult<AuthenticationResponse>> Login(
        [FromBody] LoginRequest request,
        [FromServices] LoginUserUseCase useCase,
        CancellationToken cancellationToken)
    {
        var result = await useCase.ExecuteAsync(request.ToCommand(), cancellationToken);

        return Ok(result.ToResponse());
    }

    /// <summary>
    /// Signs in with a Google ID token, registering an account the first time this Google
    /// identity is seen (or linking it to an existing password account with the same, Google
    /// verified, email address).
    /// </summary>
    [HttpPost("google")]
    [AllowAnonymous]
    [ProducesResponseType<AuthenticationResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<ActionResult<AuthenticationResponse>> Google(
        [FromBody] GoogleLoginRequest request,
        [FromServices] GoogleLoginUseCase useCase,
        CancellationToken cancellationToken)
    {
        var result = await useCase.ExecuteAsync(request.ToCommand(), cancellationToken);

        return Ok(result.ToResponse());
    }

    /// <summary>
    /// Exchanges a refresh token for a new pair. The token presented is retired in the same
    /// breath, so each one is good for exactly one use.
    /// </summary>
    [HttpPost("refresh")]
    [AllowAnonymous]
    [ProducesResponseType<AuthenticationResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<ActionResult<AuthenticationResponse>> Refresh(
        [FromBody] RefreshRequest request,
        [FromServices] RefreshTokenUseCase useCase,
        CancellationToken cancellationToken)
    {
        var result = await useCase.ExecuteAsync(request.ToCommand(), cancellationToken);

        return Ok(result.ToResponse());
    }

    /// <summary>
    /// Ends this device's session. Anonymous on purpose: a client whose access token has already
    /// expired still needs to be able to hand back its refresh token, and answering the same way
    /// for an unknown token keeps this from becoming a way to probe for live ones.
    /// </summary>
    [HttpPost("logout")]
    [AllowAnonymous]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    public async Task<IActionResult> Logout(
        [FromBody] LogoutRequest request,
        [FromServices] LogoutUserUseCase useCase,
        CancellationToken cancellationToken)
    {
        await useCase.ExecuteAsync(request.ToCommand(), cancellationToken);

        return NoContent();
    }

    /// <summary>Signs the lifter out on every device — the "I lost my phone" button.</summary>
    [HttpPost("logout-all")]
    [Authorize]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> LogoutAll(
        [FromServices] LogoutAllSessionsUseCase useCase,
        CancellationToken cancellationToken)
    {
        await useCase.ExecuteAsync(cancellationToken);

        return NoContent();
    }
}
