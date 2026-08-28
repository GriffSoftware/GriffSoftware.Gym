using GriffGym.Application.Abstractions;
using GriffGym.Application.Auth;
using GriffGym.Application.Cycles;
using GriffGym.Application.Exercises;
using GriffGym.Application.ReferenceMaxes;
using GriffGym.Application.State;
using GriffGym.Application.Users;
using GriffGym.Application.Workouts;
using Microsoft.Extensions.DependencyInjection;

namespace GriffGym.Application;

public static class DependencyInjection
{
    /// <summary>
    /// Use cases are registered one by one rather than swept up by assembly scanning.
    ///
    /// The list is the application's surface area: if something new can be done to a lifter's
    /// training data, it appears here, in a diff, where somebody has to look at it.
    /// </summary>
    public static IServiceCollection AddGriffGymApplication(this IServiceCollection services)
    {
        services.AddSingleton<IIdentifierFactory, SequentialIdentifierFactory>();
        services.AddScoped<IAuthenticationSessionService, AuthenticationSessionService>();

        services.AddScoped<RegisterUserUseCase>();
        services.AddScoped<LoginUserUseCase>();
        services.AddScoped<GoogleLoginUseCase>();
        services.AddScoped<RefreshTokenUseCase>();
        services.AddScoped<LogoutUserUseCase>();
        services.AddScoped<LogoutAllSessionsUseCase>();

        services.AddScoped<GetCurrentUserUseCase>();
        services.AddScoped<DeleteCurrentUserAccountUseCase>();

        services.AddScoped<GetReferenceMaxesUseCase>();
        services.AddScoped<UpdateReferenceMaxUseCase>();

        services.AddScoped<GetExercisesUseCase>();
        services.AddScoped<SynchroniseExercisesUseCase>();

        services.AddScoped<CreateTrainingCycleUseCase>();
        services.AddScoped<GetTrainingCyclesUseCase>();
        services.AddScoped<GetTrainingCycleUseCase>();
        services.AddScoped<CompleteTrainingCycleUseCase>();
        services.AddScoped<UpdateCycleProgressUseCase>();

        services.AddScoped<CreateWorkoutSessionUseCase>();
        services.AddScoped<UpdateWorkoutSessionUseCase>();
        services.AddScoped<LogSetUseCase>();
        services.AddScoped<CompleteWorkoutSessionUseCase>();
        services.AddScoped<CancelWorkoutSessionUseCase>();
        services.AddScoped<GetWorkoutSessionUseCase>();
        services.AddScoped<GetActiveWorkoutUseCase>();
        services.AddScoped<GetWorkoutHistoryUseCase>();

        services.AddScoped<GetUserApplicationStateUseCase>();

        return services;
    }
}
