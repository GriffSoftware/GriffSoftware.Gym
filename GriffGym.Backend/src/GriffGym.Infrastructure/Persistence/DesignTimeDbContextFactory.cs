using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace GriffGym.Infrastructure.Persistence;

/// <summary>
/// Builds a context for <c>dotnet ef</c> alone.
///
/// Without this, the tooling boots the whole web host just to find a DbContext — which means
/// generating a migration needs the API's configuration, its signing key and, depending on the
/// day, a reachable database. Adding a migration should need none of those: no connection is
/// opened here, the provider is only present so EF knows it is targeting PostgreSQL and can
/// pick the right column types.
/// </summary>
internal sealed class DesignTimeDbContextFactory : IDesignTimeDbContextFactory<GriffGymDbContext>
{
    public GriffGymDbContext CreateDbContext(string[] args)
    {
        var connectionString =
            Environment.GetEnvironmentVariable("ConnectionStrings__GriffGym")
            ?? "Host=localhost;Port=5432;Database=griffgym;Username=griffgym;Password=griffgym";

        var options = new DbContextOptionsBuilder<GriffGymDbContext>()
            .UseNpgsql(
                connectionString,
                npgsql => npgsql.MigrationsAssembly(typeof(GriffGymDbContext).Assembly.FullName))
            .Options;

        return new GriffGymDbContext(options);
    }
}
