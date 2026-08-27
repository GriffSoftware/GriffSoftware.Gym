using Microsoft.AspNetCore.OpenApi;
using Microsoft.OpenApi;

namespace GriffGym.Api.OpenApi;

/// <summary>
/// Declares the bearer scheme in the OpenAPI document so Swagger UI grows an "Authorize" box.
///
/// Without it every protected endpoint in the UI answers 401 and the documentation is only
/// useful for reading, not for trying anything.
/// </summary>
internal sealed class BearerSecuritySchemeTransformer : IOpenApiDocumentTransformer
{
    public Task TransformAsync(
        OpenApiDocument document,
        OpenApiDocumentTransformerContext context,
        CancellationToken cancellationToken)
    {
        document.Components ??= new OpenApiComponents();
        document.Components.SecuritySchemes ??= new Dictionary<string, IOpenApiSecurityScheme>();

        document.Components.SecuritySchemes["Bearer"] = new OpenApiSecurityScheme
        {
            Type = SecuritySchemeType.Http,
            Scheme = "bearer",
            BearerFormat = "JWT",
            In = ParameterLocation.Header,
            Description =
                "Paste the accessToken from POST /api/v1/auth/login. Swagger adds the 'Bearer ' prefix.",
        };

        document.Security =
        [
            new OpenApiSecurityRequirement
            {
                [new OpenApiSecuritySchemeReference("Bearer", document)] = [],
            },
        ];

        return Task.CompletedTask;
    }
}
