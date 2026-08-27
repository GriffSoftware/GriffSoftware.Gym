namespace GriffGym.Application.Common;

/// <summary>
/// One page of a list, with enough context for a client to know whether to ask for more.
///
/// Workout history is unbounded — a lifter three years in has hundreds of sessions, each with
/// its own exercises and sets — so it is never returned in one response.
/// </summary>
public sealed record PagedResult<T>(
    IReadOnlyList<T> Items,
    int Page,
    int PageSize,
    long TotalCount)
{
    public int TotalPages => PageSize <= 0 ? 0 : (int)Math.Ceiling(TotalCount / (double)PageSize);

    public bool HasNextPage => Page < TotalPages;
}

/// <summary>Page request, clamped so a client cannot ask for a million rows at once.</summary>
public readonly record struct PageRequest
{
    public const int DefaultPageSize = 20;
    public const int MaxPageSize = 100;

    public PageRequest(int page, int pageSize)
    {
        Page = page < 1 ? 1 : page;
        PageSize = pageSize switch
        {
            < 1 => DefaultPageSize,
            > MaxPageSize => MaxPageSize,
            _ => pageSize,
        };
    }

    public int Page { get; }

    public int PageSize { get; }

    public int Skip => (Page - 1) * PageSize;
}
