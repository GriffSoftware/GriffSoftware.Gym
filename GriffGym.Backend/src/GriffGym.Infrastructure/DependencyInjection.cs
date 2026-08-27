using GriffGym.Application.Abstractions;
using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Application.Abstractions.Security;
using GriffGym.Application.Auth;
using GriffGym.Infrastructure.Persistence;
using GriffGym.Infrastructure.Persistence.Interceptors;
using GriffGym.Infrastructure.Persistence.Repositories;
using GriffGym.Infrastructure.Security;
using GriffGym.Infrastructure.Time;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

namespace GriffGym.Infrastructure;

public static class DependencyInjection
{
    public const string ConnectionStringName = "GriffGym";

    public static IServiceCollection AddGriffGymInfrastructure(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        services.AddSingleton<IClock, SystemClock>();

        services.AddOptions<JwtOptions>()
            .Bind(configuration.GetSection(JwtOptions.SectionName))
            .ValidateDataAnnotations()
            .Validate(
                options =>
                    System.Text.Encoding.UTF8.GetByteCount(options.SigningKey)
                    >= JwtOptions.MinimumSigningKeyBytes,
                $"Jwt:SigningKey must be at least {JwtOptions.MinimumSigningKeyBytes} bytes.")
            // On startup, not on the first request. A deployment missing its signing key should
            // fail to boot, loudly, rather than start and hand out tokens.
            .ValidateOnStart();

        // The application layer reasons about token lifetimes; it must not see the signing key,
        // so the two settings it needs are projected across rather than sharing one options type.
        services.AddOptions<AuthenticationSettings>()
            .Configure<Microsoft.Extensions.Options.IOptions<JwtOptions>>((settings, jwt) =>
            {
                settings.AccessTokenLifetime = TimeSpan.FromMinutes(jwt.Value.AccessTokenMinutes);
                settings.RefreshTokenLifetime = TimeSpan.FromDays(jwt.Value.RefreshTokenDays);
            });

        services.AddSingleton<IPasswordHasher, PasswordHasherAdapter>();
        services.AddSingleton<IRefreshTokenGenerator, RefreshTokenGenerator>();
        services.AddScoped<IAccessTokenIssuer, JwtAccessTokenIssuer>();

        services.AddScoped<SyncMetadataInterceptor>();

        services.AddDbContext<GriffGymDbContext>((provider, options) =>
        {
            options.UseNpgsql(
                configuration.GetConnectionString(ConnectionStringName),
                npgsql => npgsql
                    .MigrationsAssembly(typeof(GriffGymDbContext).Assembly.FullName)
                    // A phone in a basement gym is not the only thing on a flaky connection;
                    // so is a VPS talking to its database. Transient failures are retried.
                    .EnableRetryOnFailure(maxRetryCount: 3, TimeSpan.FromSeconds(5), null));

            options.AddInterceptors(provider.GetRequiredService<SyncMetadataInterceptor>());
        });

        services.AddScoped<IUnitOfWork, UnitOfWork>();
        services.AddGriffGymRepositories();

        return services;
    }

    /// <summary>
    /// Each repository is registered three times over: as itself, as the contract the
    /// application layer asks for, and as one of the flushes the unit of work runs before
    /// saving. All three must resolve to the same instance, or the aggregate a use case
    /// mutated and the one that gets written back are different objects.
    /// </summary>
    private static IServiceCollection AddGriffGymRepositories(this IServiceCollection services)
    {
        services.AddScopedRepository<UserRepository, IUserRepository>();
        services.AddScopedRepository<RefreshTokenRepository, IRefreshTokenRepository>();
        services.AddScopedRepository<ExerciseRepository, IExerciseRepository>();
        services.AddScopedRepository<ReferenceMaxRepository, IReferenceMaxRepository>();
        services.AddScopedRepository<TrainingCycleRepository, ITrainingCycleRepository>();
        services.AddScopedRepository<WorkoutSessionRepository, IWorkoutSessionRepository>();

        return services;
    }

    private static void AddScopedRepository<TImplementation, TContract>(
        this IServiceCollection services)
        where TImplementation : class, TContract, IPersistenceFlush
        where TContract : class
    {
        services.AddScoped<TImplementation>();
        services.AddScoped<TContract>(provider => provider.GetRequiredService<TImplementation>());
        services.AddScoped<IPersistenceFlush>(provider =>
            provider.GetRequiredService<TImplementation>());
    }
}
