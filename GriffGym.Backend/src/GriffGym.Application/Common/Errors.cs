namespace GriffGym.Application.Common;

/// <summary>
/// A resource the caller asked for does not exist — or exists and is not theirs.
///
/// Those two cases deliberately produce the same 404. Answering 403 for somebody else's
/// workout would confirm that the identifier is real, which is a membership oracle: hand an
/// attacker a list of GUIDs and they learn which ones exist. Ownership failures are therefore
/// indistinguishable from absence at the API boundary.
/// </summary>
public sealed class NotFoundException(string resource, object key)
    : Exception($"{resource} '{key}' was not found.")
{
    public string Resource { get; } = resource;
}

/// <summary>The request cannot be applied to the current state — a duplicate email, a lost update.</summary>
public sealed class ConflictException(string message) : Exception(message);

/// <summary>
/// The client wrote against a revision that is no longer current: another device changed the
/// record first. Reported separately from a plain conflict so a future sync engine can tell
/// "you are behind, re-read and merge" from "this can never work".
/// </summary>
public sealed class ConcurrencyConflictException(string resource, int expectedVersion, int actualVersion)
    : Exception(
        $"{resource} has moved on: expected version {expectedVersion}, found {actualVersion}.")
{
    public string Resource { get; } = resource;

    public int ExpectedVersion { get; } = expectedVersion;

    public int ActualVersion { get; } = actualVersion;
}

/// <summary>Credentials were missing, wrong, expired or revoked.</summary>
public sealed class AuthenticationFailedException(string message) : Exception(message);

/// <summary>No usable access token was presented at all.</summary>
public sealed class UnauthenticatedException() : Exception("Authentication is required.");
