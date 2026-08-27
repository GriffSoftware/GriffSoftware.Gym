using System.Security.Cryptography;
using System.Text;
using GriffGym.Application.Abstractions.Security;

namespace GriffGym.Infrastructure.Security;

/// <summary>
/// Mints refresh tokens and hashes them for storage.
///
/// The token is 256 bits from the OS cryptographic generator — not a GUID, which is neither
/// secret nor uniformly random. What goes in the database is a SHA-256 digest of it: a stolen
/// backup then contains nothing that can be replayed, exactly as it contains no usable
/// passwords.
///
/// A plain digest, not PBKDF2. That is deliberate and different from the password case: the
/// input here is already 256 bits of entropy, so there is no dictionary to attack and a work
/// factor would only make every refresh slower. Passwords, which are guessable, get the slow
/// hash instead.
/// </summary>
internal sealed class RefreshTokenGenerator : IRefreshTokenGenerator
{
    private const int TokenBytes = 32;

    public RefreshTokenMaterial Generate()
    {
        var value = Convert.ToBase64String(RandomNumberGenerator.GetBytes(TokenBytes));
        return new RefreshTokenMaterial(value, HashPresented(value));
    }

    public string HashPresented(string token) =>
        Convert.ToBase64String(SHA256.HashData(Encoding.UTF8.GetBytes(token)));
}
