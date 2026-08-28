using FluentValidation;
using GriffGym.Api.Contracts.V1;
using GriffGym.Domain.Users;

namespace GriffGym.Api.Validation;

/// <summary>
/// Password policy, in one place.
///
/// Length and nothing else, deliberately. Composition rules — "one digit, one symbol, one
/// capital" — reliably produce <c>Password1!</c> and are no longer recommended by anybody who
/// has measured their effect. A floor of eight characters, a ceiling high enough for a
/// passphrase, and a cap so nobody can post a megabyte into a PBKDF2 call.
/// </summary>
public static class PasswordPolicy
{
    public const int MinimumLength = 8;
    public const int MaximumLength = 128;
}

public sealed class RegisterRequestValidator : AbstractValidator<RegisterRequest>
{
    public RegisterRequestValidator()
    {
        RuleFor(request => request.Email)
            .NotEmpty()
            .MaximumLength(EmailAddress.MaxLength)
            .Must(email => EmailAddress.IsPlausible(email.Trim()))
            .WithMessage("'{PropertyName}' is not a valid email address.");

        RuleFor(request => request.Password)
            .NotEmpty()
            .MinimumLength(PasswordPolicy.MinimumLength)
            .MaximumLength(PasswordPolicy.MaximumLength)
            .Must(password => !string.IsNullOrWhiteSpace(password))
            .WithMessage("'{PropertyName}' cannot be only whitespace.");

        RuleFor(request => request.DeviceId).MaximumLength(128);
    }
}

public sealed class LoginRequestValidator : AbstractValidator<LoginRequest>
{
    public LoginRequestValidator()
    {
        // Deliberately looser than registration. Validating a login against the current policy
        // would tell an attacker which rules an existing password satisfies, and would lock out
        // anybody whose password predates a policy change.
        RuleFor(request => request.Email).NotEmpty().MaximumLength(EmailAddress.MaxLength);
        RuleFor(request => request.Password).NotEmpty().MaximumLength(PasswordPolicy.MaximumLength);
        RuleFor(request => request.DeviceId).MaximumLength(128);
    }
}

public sealed class GoogleLoginRequestValidator : AbstractValidator<GoogleLoginRequest>
{
    public GoogleLoginRequestValidator()
    {
        // A Google ID token is a JWT carrying the account's profile claims, comfortably a few KB
        // — nothing like a password's length policy applies here.
        RuleFor(request => request.IdToken).NotEmpty().MaximumLength(8192);
        RuleFor(request => request.DeviceId).MaximumLength(128);
    }
}

public sealed class RefreshRequestValidator : AbstractValidator<RefreshRequest>
{
    public RefreshRequestValidator()
    {
        RuleFor(request => request.RefreshToken).NotEmpty().MaximumLength(512);
        RuleFor(request => request.DeviceId).MaximumLength(128);
    }
}

public sealed class LogoutRequestValidator : AbstractValidator<LogoutRequest>
{
    public LogoutRequestValidator() =>
        RuleFor(request => request.RefreshToken).NotEmpty().MaximumLength(512);
}
