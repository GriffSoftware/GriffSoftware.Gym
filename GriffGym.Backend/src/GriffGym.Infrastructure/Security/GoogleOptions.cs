namespace GriffGym.Infrastructure.Security;

/// <summary>
/// Google Sign-In configuration.
///
/// Unlike <see cref="JwtOptions"/>, <see cref="WebClientId"/> is not required at startup:
/// Google sign-in is an additional login method, not a replacement for email/password, so a
/// deployment that has not configured it yet still boots and serves everything else. Calling
/// <c>/auth/google</c> before it is configured fails that one request, not the whole process.
/// </summary>
public sealed class GoogleOptions
{
    public const string SectionName = "Google";

    /// <summary>
    /// The OAuth 2.0 "Web application" client ID from Google Cloud Console — the audience every
    /// Google ID token must be issued for. Despite the name, this is what the Android app's
    /// Credential Manager request is configured with too (as <c>serverClientId</c>); a separate
    /// "Android" OAuth client also has to exist in the same project (tied to the app's package
    /// name and signing certificate) for Google to accept the request at all, but its client ID
    /// is never used in code. See docs/GOOGLE_SIGN_IN.md.
    /// </summary>
    public string WebClientId { get; init; } = string.Empty;
}
