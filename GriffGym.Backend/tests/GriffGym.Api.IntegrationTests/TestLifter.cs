using System.Net.Http.Headers;
using System.Net.Http.Json;
using GriffGym.Api.Contracts.V1;

namespace GriffGym.Api.IntegrationTests;

/// <summary>
/// One registered lifter with an authenticated client, so tests read as "user A cannot see
/// user B's workout" rather than as a paragraph of token plumbing.
/// </summary>
public sealed class TestLifter
{
    private TestLifter(HttpClient client, AuthenticationResponse credentials)
    {
        Client = client;
        Credentials = credentials;
    }

    public HttpClient Client { get; }

    public AuthenticationResponse Credentials { get; }

    public Guid Id => Credentials.UserId;

    public string Email => Credentials.Email;

    public static async Task<TestLifter> RegisterAsync(
        GriffGymApiFactory factory,
        string? email = null,
        string password = "correct horse battery")
    {
        var client = factory.CreateClient();

        var response = await client.PostAsJsonAsync(
            "/api/v1/auth/register",
            new RegisterRequest(email ?? $"lifter-{Guid.NewGuid():N}@example.com", password, "pixel-9"),
            GriffGymApiFactory.Json);

        response.EnsureSuccessStatusCode();

        var credentials = await response.ReadAsync<AuthenticationResponse>();

        client.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", credentials.AccessToken);

        return new TestLifter(client, credentials);
    }
}

public static class HttpResponseExtensions
{
    public static async Task<T> ReadAsync<T>(this HttpResponseMessage response)
    {
        var payload = await response.Content.ReadFromJsonAsync<T>(GriffGymApiFactory.Json);

        return payload ?? throw new InvalidOperationException(
            $"Expected a {typeof(T).Name} body but the response was empty. "
            + $"Status {(int)response.StatusCode}.");
    }

    /// <summary>Includes the body in the failure message; a bare status code is rarely enough.</summary>
    public static async Task<T> ReadSuccessAsync<T>(this HttpResponseMessage response)
    {
        if (!response.IsSuccessStatusCode)
        {
            var body = await response.Content.ReadAsStringAsync();
            throw new InvalidOperationException(
                $"Expected success but got {(int)response.StatusCode}. Body: {body}");
        }

        return await response.ReadAsync<T>();
    }
}
