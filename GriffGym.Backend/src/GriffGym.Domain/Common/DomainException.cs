namespace GriffGym.Domain.Common;

/// <summary>
/// A business rule was broken — "you cannot complete a cycle twice", "RPE 12 does not exist".
///
/// Deliberately distinct from an argument exception: the API maps this to 422 Unprocessable
/// Entity, because the request was well formed but asked for something the domain forbids.
/// </summary>
public sealed class DomainException : Exception
{
    public DomainException(string message) : base(message)
    {
    }

    public static void Require(bool condition, string message)
    {
        if (!condition)
        {
            throw new DomainException(message);
        }
    }
}
