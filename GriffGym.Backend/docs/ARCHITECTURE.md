# Architecture

This document covers the decisions behind the Griff Gym API — the ones that were choices rather
than conventions, and the reasoning that will otherwise be lost.

---

## Layers

```
             ┌──────────────────────┐
             │     GriffGym.Api     │  controllers, HTTP contracts, validation, errors
             └──────────┬───────────┘
                        │
             ┌──────────▼───────────┐
             │ GriffGym.Application │  use cases, commands, read models, contracts
             └──────────┬───────────┘
                        │
             ┌──────────▼───────────┐
             │   GriffGym.Domain    │  training model and its rules
             └──────────▲───────────┘
                        │
             ┌──────────┴───────────┐
             │GriffGym.Infrastructure│ EF Core, PostgreSQL, hashing, tokens
             └───────────────────────┘
```

`GriffGym.Domain.csproj` has **no `PackageReference` elements at all**. That is the enforcement
mechanism: the domain cannot reference ASP.NET Core, EF Core, Npgsql, HTTP or JWT, because those
assemblies are not on its compile path. The rule does not depend on anybody remembering it.

Infrastructure depends on Application and Domain and implements their contracts. Nothing depends
on Infrastructure except `Program.cs`, which composes the whole thing.

### Use cases, not a mediator

The Application layer is explicit classes — `CreateTrainingCycleUseCase`, `LogSetUseCase` — each
registered by name in `AddGriffGymApplication`. No MediatR.

MediatR earns its keep when there are cross-cutting behaviours worth putting in a pipeline and
many handlers to apply them to. Here there are around twenty use cases and the two cross-cutting
concerns — validation and error mapping — already have first-class homes in ASP.NET Core (an
action filter and an `IExceptionHandler`). Adding a mediator would buy indirection and a
`Send(object)` call site that no longer tells you what runs.

The registration list is also the system's surface area. If something new can be done to a
lifter's training data, it appears in that list, in a diff, where somebody has to look at it.

### Repositories, not `IRepository<T>`

There is no generic repository. Every method on `IWorkoutSessionRepository` exists because a use
case needs exactly it, and almost every one takes a `userId` — not because a base class demanded
a parameter, but because "whose data is this?" is a question the persistence layer must never be
able to skip.

`CountCompletedByWeekAsync` is a good example of why: it exists because cycle progress is counted
in the database rather than by materialising three years of sessions. A generic `GetAll` would
have made that impossible to express.

---

## Three models, not one

```
Domain model            Persistence entity           API contract
WorkoutSession     ≠    WorkoutSessionRecord    ≠    WorkoutResponse
```

- **Domain** — rich and self-validating. Private setters, behaviour-bearing methods, invariants
  checked in constructors. `WorkoutSession.LogSet` refuses to write into a finished session.
- **Persistence** (`Infrastructure/Persistence/Entities`) — flat bags of columns EF Core knows how
  to write, plus navigation properties and sync metadata. All `internal`.
- **API** (`Api/Contracts/V1`) — what goes on the wire.

The separation costs mapping code. It buys three things:

1. An EF entity exposed as JSON leaks column names and navigation properties, and — worse —
   *accepts* them on the way in. That is mass assignment by default. There is no `UserId` field
   on any request contract in this API, so no request can assert whose data it is writing.
2. A v2 of the API can change shape without the domain moving underneath it.
3. Sync metadata (`Version`, `SyncVersion`, `DeletedAtUtc`) is maintained by the database layer
   and lives on the persistence entity, where an interceptor can stamp it, rather than being
   something every use case has to remember.

The mappers in `Infrastructure/Persistence/Mappers` are the only place the domain and persistence
models meet.

### How changes get from the domain object to the row

EF Core tracks the persistence record; use cases mutate the domain object. Something has to carry
changes between them, and making every use case call `repository.Update(aggregate)` means a
forgotten call silently drops a lifter's sets.

Instead, `TrackedRepository<TDomain, TRecord>` keeps each aggregate beside the row it came from
for the life of the request, and `UnitOfWork` flushes all of them just before `SaveChanges`:

```
use case mutates domain aggregate
        │
        ▼
UnitOfWork.SaveChangesAsync
        │  Flush()          domain ──► record, for every tracked aggregate
        │  SaveChanges      EF works out the SQL
        │  RefreshAfterSave record ──► domain, so the response carries the revision that was written
        ▼
```

Two details that are load-bearing:

- **A repeat read returns the same aggregate.** Two reads of one workout inside a request must
  hand back the same object, or one will write over the other's changes.
- **The root's timestamp is copied up on flush.** A logged set changes a `set_log` row, not the
  `workout_session` row. Without copying the aggregate's own `UpdatedAtUtc` onto the root record,
  EF would see the root as untouched, its version would never advance, and optimistic concurrency
  on a workout would protect nothing. This was caught by a test, not by inspection.

---

## Identifiers: client-generated GUIDs

Every synchronised record has a `uuid` primary key, and `ValueGeneratedNever()`. The client is
allowed to invent it.

This is not a preference. The product has a stated future in which a lifter uses Griff Gym purely
locally for six months, accumulating cycles and hundreds of sessions, and only then creates an
account and uploads all of it. Those rows already have identities. A server that assigned its own
would force a full remapping of every foreign key in the phone's database at the moment of
migration — the point at which a bug is least recoverable and least visible.

So: **nothing in this model assumes the server saw a record first.** `POST /api/v1/cycles` and
`POST /api/v1/workouts` both accept an `id`, and both are idempotent on it.

Where the server does mint an identifier — refresh tokens, and any record a client left blank —
it uses `Guid.CreateVersion7()`. Sequential, so primary key inserts stay clustered rather than
scattering writes across the whole index the way v4 does.

---

## Time

Every timestamp is `DateTimeOffset`, stored as PostgreSQL `timestamptz`, and every column is named
`..._at_utc`. A global value converter in `OnModelCreating` calls `ToUniversalTime()` on the way in
and out, so a client sending `+02:00` is stored as the instant it means and no code path can
forget to normalise.

`WorkoutSession.PerformedOn` is a `DateOnly`, not a timestamp. "Which day did I train?" is a
question about the lifter's calendar, and a session that starts at 23:40 local time belongs to
that day, not to the UTC one.

Time is injected as `IClock` rather than read from `DateTimeOffset.UtcNow`, so token expiry, cycle
completion and session duration can be tested without waiting.

---

## Numbers

Kilograms are `numeric(7,2)` and `decimal` in C#. Never `float`, never `double`, never `int`.

Loads on this program move in 2.5 kg and 1.25 kg steps, so 117.5, 132.5, 152.5 and 162.5 are
ordinary. A weight that reads back as `117.49999999999999` is a corrupted training log, and the
corruption would be silent and permanent. There is a test that writes half-kilogram loads and
asserts they come back exactly.

RPE is `numeric(3,1)`, validated as 1.0–10.0 in half steps. A client sending 7.31 is rejected
rather than rounded: it has a bug, and quietly storing 7.5 would hide it.

Session tonnage is `numeric(12,2)` — a session runs to tens of thousands of kilograms.

---

## Plan versus actual

The single most important rule in the model.

The training plan is a **template**. Starting a workout *copies* it: every prescribed set becomes
its own `set_log` row carrying `planned_weight_kg`, `planned_reps`, `planned_rpe_min` and
`planned_rpe_max`. Results go into separate `actual_*` columns.

```
training_cycle ──► training_program ──► training_week ──► workout_template
                                                                │
                                                       exercise_template
                                                                │
                                                          planned_set

                                    ── START copies the tree ──►

workout_session ──► exercise_log ──► set_log   (planned_* snapshot + actual_* results)
```

Nothing on the write path lets an actual value overwrite a planned one. `WorkoutSessionMapper`
writes the planned columns exactly once, when the row is created, and never touches them again.

`workout_session` also copies in week number, day number, title and the deload flag rather than
reading them through `workout_template_id`. That link is provenance only and is `ON DELETE SET
NULL`. Deleting a cycle takes its plan and leaves the training log standing — there is a test for
precisely that, asserting the session, its exercises and its sets survive with their snapshots
intact.

Storing the whole plan, rather than "cycle 3" and a rule for regenerating it, is what makes
history immutable. If the block the app ships ever changes, cycle 1 still restores as the block
that was actually trained.

---

## Concurrency and sync metadata

Two numbers on every synchronised table, doing two different jobs.

### `version` — optimistic concurrency

An `integer`, configured `IsConcurrencyToken()`. Every update writes `version + 1` and matches on
the version it read. A write from a phone holding stale data fails instead of silently discarding
the other device's sets.

Clients send the revision they hold as `expectedVersion`. A mismatch is a `409` carrying both
`expectedVersion` and `actualVersion` in the problem document, so the client can re-read, merge
and write again. Omitting it is last-write-wins, which is only right for a device that knows it is
the only one writing.

`xmin` was the alternative. It was not chosen because it is meaningful only inside PostgreSQL: a
mobile client cannot hold it, reason about it, or send it back, and this token has to survive a
round trip through a phone that has been offline for a week.

### `sync_version` — the delta cursor

A `bigint` drawn from one database sequence, `griffgym_sync_version`. Not per table: "what changed
since 4 812?" has to have a single answer across cycles, workouts and reference maxes at once.

`SyncMetadataInterceptor` takes **one** value per `SaveChanges` and stamps it on every row that
call touched. That is deliberate — a future delta query asking for everything above a cursor then
receives whole transactions, never half a cycle and half its program. Every synchronised table is
indexed on it.

`GET /api/v1/state` returns the highest value it saw as `syncVersion`. That is the cursor Phase 2
will resume from.

### Aggregate-level, not row-level

Versions live on aggregate roots — `user`, `exercise`, `reference_max`, `training_cycle`,
`workout_session` — and not on their children. A workout syncs as one unit: shipping half a
session would produce a state no client could render. The flush step copies the root's
`UpdatedAtUtc` up whenever anything inside the aggregate changes, which is what makes the root's
version move with its children.

---

## Deletions: tombstones, not `DELETE`

Every synchronised table has a nullable `deleted_at_utc`.

A hard delete is invisible to a device that was offline when it happened. That device comes back,
asks for everything above its cursor, sees nothing about the row, and keeps it forever. The
tombstone is how a removal becomes something that can be *told* to a client: the row's
`sync_version` advances like any other change, and the delta query returns it.

Phase 1 never deletes anything. The column exists, is indexed alongside the cursor, and every read
path already filters `deleted_at_utc IS NULL`, so the delta endpoint can be added without a
migration that touches every table. There is a test asserting the column is present on all five
synchronised tables.

Explicit filtering was chosen over EF global query filters: a filter on a principal that has
required dependents produces a model-level warning and surprising behaviour, and being able to see
the predicate at the call site is worth more than the brevity.

---

## Ownership

The rule: **user A can never obtain user B's data.**

- Identity comes from the validated access token's `sub` claim and nowhere else. No route value,
  query string or request body can name a user. `ICurrentUser` is the only source.
- Ownership is part of the query, not a check afterwards. `FindForUserAsync(userId, cycleId)` is
  the only way to load a cycle; there is no "load by id, then check owner" path to forget.
- A record that belongs to somebody else returns **404, not 403**. Answering "forbidden" would
  confirm the identifier is real — hand an attacker a list of GUIDs and they learn which ones
  exist. Absence and denial are indistinguishable at the boundary.
- There is no `GET /users/{id}`. The endpoint that would allow one account to read another does
  not exist rather than being guarded.

`SecurityOwnershipTests` covers this for read, update, complete, log-set, progress, listing and
the state document.

---

## Authentication

Passwords go through `Microsoft.AspNetCore.Identity.PasswordHasher<T>` — PBKDF2-HMAC-SHA512, a
per-password salt, a large iteration count, and a fixed-time comparison. No hashing scheme is
invented here. The hasher also reports when a stored hash used older parameters, which is what
lets a work-factor increase reach accounts whose owners never change their password.

Login verifies against a decoy hash when the account does not exist. Without it, "no such account"
returns in microseconds while "wrong password" spends the full work factor, and the difference is
a free account-enumeration oracle.

Refresh tokens are 256 bits from the OS cryptographic generator, and only a SHA-256 digest is
stored. A plain digest, not PBKDF2 — deliberately different from the password case: the input is
already 256 bits of entropy, so there is no dictionary to attack and a work factor would only make
every refresh slower.

Rotation, and what happens when it is violated:

```
Refresh Token A ──refresh──► Refresh Token B      A is revoked, and records that B replaced it
       │
       └── presented again ──► every session for that account is revoked
```

A token that was already exchanged being presented again means two parties hold the same secret.
Which one is the thief is unknowable, so both are signed out. Sessions are per device — a lifter
holds several at once — so this is a real cost, and it is the right one.

Access tokens carry a `sstamp` claim holding the user's security stamp, so a future password
change can invalidate tokens already in the wild instead of waiting for them to expire.

`ClockSkew` is set to zero. The framework's default five minutes quietly extends a fifteen-minute
access token by a third.

---

## Errors

One `IExceptionHandler` maps everything to RFC 9457 `ProblemDetails`:

| Exception | Status | Meaning |
|---|---|---|
| `ValidationException` (FluentValidation) | 400 | Malformed request, with per-field errors |
| `NotFoundException` | 404 | Absent — or somebody else's |
| `ConcurrencyConflictException` | 409 | Stale revision; carries `expectedVersion` / `actualVersion` |
| `ConflictException` | 409 | Duplicate email, second active workout, taken identifier |
| `DomainException` | 422 | Well-formed, but the rules forbid it |
| `AuthenticationFailedException` | 401 | Bad, expired or revoked credentials |
| anything else | 500 | Fixed sentence to the client, detail to the log |

422 rather than 400 for a domain violation is the distinction worth keeping: completing a cycle
twice is a well-formed request asking for something impossible, not a malformed one.

Validation runs in an action filter that resolves `IValidator<T>` for each action argument, rather
than a `ValidateAndThrow` call at the top of each action — a controller that forgets the call is
an endpoint with no validation at all, and that is not the sort of mistake review catches.

---

## API versioning

Everything is under `/api/v1`. The prefix is one constant, and v1's contracts, controllers and
mappers each live in their own `V1` namespace.

That is the whole strategy, and it is deliberately not a library. Adding v2 means adding
`Controllers/V2` beside v1 with its own contracts: v1 keeps working, untouched, and the two can
disagree about shape for as long as clients need them to. A versioning package would buy header
and media-type negotiation that a single first-party mobile client will never use.

---

## What was built now for sync later

Phase 1 deliberately does not implement bidirectional sync, conflict resolution, delta queries or
a background sync worker. It does make all of them possible without a migration that rewrites the
schema:

| Need | Already in place |
|---|---|
| Phone-generated identities | `uuid` primary keys, `ValueGeneratedNever`, idempotent creates |
| Retries after a timeout | `POST` returns 200 and the existing record instead of a duplicate |
| Detecting conflicts | `version` concurrency token, `expectedVersion`, 409 with both numbers |
| Paging changes | `sync_version` from one global sequence, indexed, one value per transaction |
| Propagating deletes | `deleted_at_utc` on every synchronised table, filtered on read |
| Rebuilding a device | `GET /api/v1/state`, with `schemaVersion` and a resume cursor |
| Uploading local history | Sessions can be created already `Completed`, with client identifiers |

The `FullRestoreTests` suite is the check that this holds together: it registers a lifter, sets
their maxes, builds a cycle, trains and finishes a workout, starts another and leaves it open,
then asserts the state document contains everything needed to rebuild the installation — planned
values, actual values, the progress pointer, and the open session's half-finished sets.
