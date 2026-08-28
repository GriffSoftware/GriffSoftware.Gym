# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------------------------
# Build
#
# Project files are copied and restored before the source is, so that editing a .cs file does
# not invalidate the restore layer. On a normal edit-and-rebuild loop that is the difference
# between a few seconds and pulling the whole package graph again.
# ---------------------------------------------------------------------------------------------
FROM mcr.microsoft.com/dotnet/sdk:10.0-noble AS build
ARG BUILD_CONFIGURATION=Release
WORKDIR /src

COPY global.json nuget.config Directory.Build.props Directory.Packages.props ./
COPY .config/ .config/
COPY src/GriffGym.Domain/GriffGym.Domain.csproj                 src/GriffGym.Domain/
COPY src/GriffGym.Application/GriffGym.Application.csproj       src/GriffGym.Application/
COPY src/GriffGym.Infrastructure/GriffGym.Infrastructure.csproj src/GriffGym.Infrastructure/
COPY src/GriffGym.Api/GriffGym.Api.csproj                       src/GriffGym.Api/

RUN dotnet restore src/GriffGym.Api/GriffGym.Api.csproj

COPY src/ src/

RUN dotnet publish src/GriffGym.Api/GriffGym.Api.csproj \
    --configuration $BUILD_CONFIGURATION \
    --no-restore \
    --output /app/publish

# ---------------------------------------------------------------------------------------------
# Migrate
#
# Applying a migration needs the SDK, the EF Core CLI tooling and the Infrastructure project's
# design-time factory — none of which belong in the runtime image that actually serves traffic.
# This stage forks off `build` instead, and is only ever built as its own compose service.
# ---------------------------------------------------------------------------------------------
FROM build AS migrate

RUN dotnet tool restore

ENTRYPOINT ["dotnet", "dotnet-ef", "database", "update", \
    "--project", "src/GriffGym.Infrastructure", \
    "--startup-project", "src/GriffGym.Api"]

# ---------------------------------------------------------------------------------------------
# Runtime
#
# The ASP.NET runtime image, not the SDK: the compiler, the NuGet cache and the source have no
# business being on a public-facing host.
# ---------------------------------------------------------------------------------------------
FROM mcr.microsoft.com/dotnet/aspnet:10.0-noble AS final
WORKDIR /app

ENV ASPNETCORE_ENVIRONMENT=Production \
    ASPNETCORE_HTTP_PORTS=8080 \
    DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1

COPY --from=build /app/publish .

# $APP_UID is the unprivileged account the official images ship with (uid 1654). A container
# that never needs to write outside /tmp has no reason to run as root.
USER $APP_UID

EXPOSE 8080

# Migrations are a deployment step, not a startup side effect — see README.md. The process
# starts, serves, and does not touch the schema.
ENTRYPOINT ["dotnet", "GriffGym.Api.dll"]
