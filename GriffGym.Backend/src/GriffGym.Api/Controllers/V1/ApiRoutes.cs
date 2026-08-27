namespace GriffGym.Api.Controllers.V1;

/// <summary>
/// The version prefix lives in one constant, and the v1 contracts, controllers and mappers each
/// live in their own <c>V1</c> namespace.
///
/// That is the whole versioning strategy, and it is deliberately not a library. Adding v2 means
/// adding a <c>Controllers/V2</c> beside this one with its own contracts: v1 keeps working,
/// untouched, and the two can disagree about shape for as long as clients need them to. A
/// versioning package would buy header and media-type negotiation that a single first-party
/// mobile client will never use.
/// </summary>
internal static class ApiRoutes
{
    public const string Version = "v1";
    public const string Base = $"api/{Version}";
}
