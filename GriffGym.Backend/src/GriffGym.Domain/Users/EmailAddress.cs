using System.Globalization;
using GriffGym.Domain.Common;

namespace GriffGym.Domain.Users;

/// <summary>
/// A user's email address, carried together with its normalised form.
///
/// Normalisation is upper-invariant, the same rule ASP.NET Core Identity's
/// <c>UpperInvariantLookupNormalizer</c> applies, so "Pawel@Example.com" and
/// "pawel@example.com" are one account. The unique index is on the normalised value; the
/// original is kept because it is what the lifter typed and what any future email would be
/// addressed to.
/// </summary>
public readonly record struct EmailAddress
{
    public const int MaxLength = 256;

    private EmailAddress(string value, string normalized)
    {
        Value = value;
        Normalized = normalized;
    }

    public string Value { get; }

    public string Normalized { get; }

    public static EmailAddress Of(string value)
    {
        var trimmed = (value ?? string.Empty).Trim();

        DomainException.Require(trimmed.Length > 0, "Email is required.");
        DomainException.Require(
            trimmed.Length <= MaxLength,
            $"Email must be at most {MaxLength} characters.");
        DomainException.Require(IsPlausible(trimmed), $"'{trimmed}' is not a valid email address.");

        return new EmailAddress(trimmed, Normalize(trimmed));
    }

    /// <summary>Rehydrates a row that was already validated when it was written.</summary>
    public static EmailAddress FromStorage(string value, string normalized) => new(value, normalized);

    public static string Normalize(string value) =>
        (value ?? string.Empty).Trim().ToUpper(CultureInfo.InvariantCulture);

    /// <summary>
    /// A deliberately loose structural check. Deciding whether an address can receive mail is
    /// a job for sending mail, not for a regular expression.
    /// </summary>
    public static bool IsPlausible(string value)
    {
        var at = value.IndexOf('@', StringComparison.Ordinal);
        if (at <= 0 || at != value.LastIndexOf('@') || at == value.Length - 1)
        {
            return false;
        }

        var domain = value[(at + 1)..];
        return !value.Contains(' ', StringComparison.Ordinal)
               && domain.Contains('.', StringComparison.Ordinal)
               && !domain.StartsWith('.')
               && !domain.EndsWith('.');
    }

    public override string ToString() => Value;
}
