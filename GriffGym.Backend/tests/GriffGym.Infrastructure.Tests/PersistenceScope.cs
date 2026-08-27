using GriffGym.Application.Abstractions.Persistence;
using GriffGym.Infrastructure.Persistence;
using GriffGym.Infrastructure.Persistence.Repositories;
using GriffGym.TestSupport;

namespace GriffGym.Infrastructure.Tests;

/// <summary>
/// One request's worth of persistence, composed exactly as the container composes it: a
/// context, the repositories over it, and the real unit of work holding them.
///
/// Tests go through this rather than calling <c>SaveChangesAsync</c> themselves, because the
/// unit of work is where aggregates are flushed onto their rows and where the revisions the
/// database assigned are handed back afterwards. A test that saves directly is exercising a
/// path no request ever takes — and would have quietly passed while the real one was broken.
/// </summary>
internal sealed class PersistenceScope : IAsyncDisposable
{
    private readonly GriffGymDbContext _context;

    public PersistenceScope(PostgresFixture fixture)
    {
        _context = fixture.CreateContext();

        Users = new UserRepository(_context);
        Exercises = new ExerciseRepository(_context);
        ReferenceMaxes = new ReferenceMaxRepository(_context);
        Cycles = new TrainingCycleRepository(_context);
        Sessions = new WorkoutSessionRepository(_context);
        RefreshTokens = new RefreshTokenRepository(_context);

        UnitOfWork = new UnitOfWork(
            _context,
            [Users, Exercises, ReferenceMaxes, Cycles, Sessions, RefreshTokens]);
    }

    public GriffGymDbContext Context => _context;

    public UserRepository Users { get; }

    public ExerciseRepository Exercises { get; }

    public ReferenceMaxRepository ReferenceMaxes { get; }

    public TrainingCycleRepository Cycles { get; }

    public WorkoutSessionRepository Sessions { get; }

    public RefreshTokenRepository RefreshTokens { get; }

    public IUnitOfWork UnitOfWork { get; }

    public Task SaveAsync() => UnitOfWork.SaveChangesAsync(default);

    public ValueTask DisposeAsync() => _context.DisposeAsync();
}
