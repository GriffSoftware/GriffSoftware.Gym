namespace GriffGym.Api.Contracts.V1;

/*
 * The API's own contracts, deliberately separate from both the domain model and the EF Core
 * entities. Three reasons, all of them practical rather than ceremonial:
 *
 *   - an EF entity exposed as JSON leaks column names, navigation properties and, worse,
 *     accepts them on the way in: mass assignment by default;
 *   - a v2 of this API has to be able to change shape without the domain moving underneath it;
 *   - what a request is *allowed* to set is not the same set of fields an entity *has*.
 *     A client may send a workout's notes; it may not send its owner.
 */

/// <summary>One page of results, with enough context to ask for the next.</summary>
public sealed record PagedResponse<T>(
    IReadOnlyList<T> Items,
    int Page,
    int PageSize,
    long TotalCount,
    int TotalPages,
    bool HasNextPage);
