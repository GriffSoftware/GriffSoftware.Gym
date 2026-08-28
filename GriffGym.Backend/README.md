# Griff Gym API

The backend for **Griff Gym**, a native Android strength-training log. It stores a lifter's
account, their planning numbers, their training cycles with the full plan each was built from,
and every set they have ever logged — so that a new phone can be signed into and put straight
back to the set they were on.

The Android app is offline-first and stays that way. This API is a durable copy, not a
dependency: nothing here assumes the phone is online, and nothing here assumes the server saw a
record first.

---

## Overview

| | |
|---|---|
| Platform | ASP.NET Core on .NET 10, Linux |
| Database | PostgreSQL 16, via EF Core 10 and Npgsql |
| Authentication | Email and password, JWT access tokens, rotating refresh tokens |
| API surface | `/api/v1`, JSON, OpenAPI in development |
| Deployment target | Docker on a Linux VPS behind a TLS-terminating reverse proxy |

---

## Architecture

Clean Architecture as four projects, with dependencies pointing inward:

```
GriffGym.Api  ────────►  GriffGym.Application  ────────►  GriffGym.Domain
                                   ▲                              ▲
                                   └──── GriffGym.Infrastructure ─┘
```

- **`GriffGym.Domain`** — the training model and its rules. Has *no package references at all*,
  so it cannot take a dependency on ASP.NET Core, EF Core, Npgsql, HTTP or JWT even by accident.
- **`GriffGym.Application`** — explicit use cases, one class per thing the system can do, plus
  the contracts (`IWorkoutSessionRepository`, `IPasswordHasher`, `IClock`) that infrastructure
  implements.
- **`GriffGym.Infrastructure`** — EF Core, PostgreSQL, password hashing, token issuing. Depends
  on Application and Domain; nothing depends on it except the composition root.
- **`GriffGym.Api`** — controllers, HTTP contracts, validation, error handling. `Program.cs` is
  the only file that knows all four exist.

`docs/ARCHITECTURE.md` covers the design decisions: identifiers, timestamps, concurrency,
deletions, and what was built now to make offline sync possible later.

---

## Tech stack

- .NET 10 (LTS), C# with nullable reference types treated as errors
- ASP.NET Core controllers, `ProblemDetails` (RFC 9457) for every error
- Entity Framework Core 10 with `Npgsql`, one `IEntityTypeConfiguration<T>` per table
- `Microsoft.AspNetCore.Identity.PasswordHasher<T>` for password hashing
- FluentValidation
- Built-in ASP.NET Core rate limiting
- xUnit v3 on Microsoft.Testing.Platform; Testcontainers for PostgreSQL where Docker is available

---

## Requirements

- .NET SDK 10.0.100 or later (`global.json` pins the major version)
- PostgreSQL 16, or Docker

---

## Running locally

### With Docker

```bash
docker compose up --build
```

The API listens on `http://localhost:8080` and PostgreSQL on `localhost:5432`. Compose sets
`GriffGym__ApplyMigrationsOnStartup=true`, so the schema is created on first boot.

Swagger UI: <http://localhost:8080/swagger>

### Without Docker

Start a PostgreSQL 16 and create the database:

```bash
createdb griffgym
```

Set a signing key. There is none in the repository, and the API refuses to start without one:

```bash
dotnet user-secrets set "Jwt:SigningKey" "$(openssl rand -base64 48)" --project src/GriffGym.Api
```

Point the API at the database:

```bash
export ConnectionStrings__GriffGym="Host=localhost;Port=5432;Database=griffgym;Username=griffgym;Password=griffgym"
```

```bash
dotnet tool restore && dotnet dotnet-ef database update --project src/GriffGym.Infrastructure --startup-project src/GriffGym.Api
```

```bash
dotnet run --project src/GriffGym.Api
```

---

## Configuration

**No signing key exists anywhere in this repository**, including in
`appsettings.Development.json`. Startup validation refuses to boot without one rather than falling
back to a default, so a misconfigured deployment fails immediately instead of issuing tokens
signed with something predictable. Locally, supply one through user secrets; in production,
through the environment.

(`appsettings.Development.json` does carry the docker-compose database password. That is not a
secret in any meaningful sense — the database is reachable only from the developer's machine and
holds nothing but throwaway data.)

| Setting | Environment variable | Notes |
|---|---|---|
| Connection string | `ConnectionStrings__GriffGym` | Required |
| JWT signing key | `Jwt__SigningKey` | Required, at least 32 bytes |
| JWT issuer | `Jwt__Issuer` | Default `griffgym-api` |
| JWT audience | `Jwt__Audience` | Default `griffgym-app` |
| Access token lifetime | `Jwt__AccessTokenMinutes` | Default 15 |
| Refresh token lifetime | `Jwt__RefreshTokenDays` | Default 30 |
| Migrate on startup | `GriffGym__ApplyMigrationsOnStartup` | Default `false`; leave it off in production |
| Auth rate limit | `RateLimiting__AuthenticationPermitsPerMinute` | Default 10 |
| General rate limit | `RateLimiting__GeneralPermitsPerMinute` | Default 300 |
| Google sign-in web client id | `Google__WebClientId` | Optional — see `docs/GOOGLE_SIGN_IN.md` at the repo root. Unset means `/api/v1/auth/google` alone answers unconfigured; nothing else is affected |

Generate a signing key with:

```bash
openssl rand -base64 48
```

Environments:

- **Development** — Swagger UI served, verbose EF command logging.
- **Production** — no Swagger, no framework detail in error responses. The global handler
  answers with a fixed sentence and logs the exception where the operator can see it.

---

## Database migrations

Migrations are an explicit deployment step. They do **not** run on startup in production: several
replicas booting at once would race to alter the same schema, and a bad migration would take the
service down with no way to stop it.

Create one:

```bash
dotnet dotnet-ef migrations add <Name> --project src/GriffGym.Infrastructure --startup-project src/GriffGym.Api --output-dir Persistence/Migrations
```

Apply:

```bash
dotnet dotnet-ef database update --project src/GriffGym.Infrastructure --startup-project src/GriffGym.Api
```

For a container deployment, produce an idempotent script and run it against the database before
rolling the new image out:

```bash
dotnet dotnet-ef migrations script --idempotent --project src/GriffGym.Infrastructure --startup-project src/GriffGym.Api --output migrate.sql
```

`EnsureCreated()` is not used anywhere. Workout history is the point of this system, and it does
not get dropped for convenience.

---

## Running tests

```bash
dotnet test
```

Four suites:

| Suite | What it covers | Needs a database |
|---|---|---|
| `GriffGym.Domain.Tests` | Value objects, cycle and session lifecycles, RPE and weight rules | No |
| `GriffGym.Application.Tests` | Use cases over hand-written in-memory repositories | No |
| `GriffGym.Infrastructure.Tests` | EF mappings, constraints, cascades, concurrency, sync metadata | Yes |
| `GriffGym.Api.IntegrationTests` | The real host end to end: auth, ownership, workouts, full restore | Yes |

The database-backed suites find one in this order: `GRIFFGYM_TEST_POSTGRES` if set, then
Testcontainers if a Docker daemon is reachable, then a PostgreSQL already running locally (TCP or
Unix socket). If none answers, those tests **skip with a reason** rather than failing — a red
suite that only means "no Postgres on this laptop" teaches people to ignore red suites.

To pin them to a specific server:

```bash
GRIFFGYM_TEST_POSTGRES="Host=localhost;Port=5432;Database=postgres;Username=griffgym;Password=griffgym" dotnet test
```

---

## Authentication

`POST /api/v1/auth/register` and `/login` return a short-lived JWT access token and a long-lived
refresh token. Send the access token as `Authorization: Bearer <token>`.

Refresh tokens **rotate**: using one mints a replacement and retires the original, so each is good
for exactly one use. Presenting an already-rotated token is treated as theft rather than as a
retry — every session for that account is revoked and the lifter signs in again everywhere.

Only a SHA-256 hash of each refresh token is stored. Passwords are hashed with ASP.NET Core
Identity's PBKDF2 implementation, and are re-hashed automatically on the next successful login if
the stored hash predates a work-factor change.

`POST /api/v1/auth/google` is a second way to reach the same token pair: it verifies a Google ID
token instead of a password, registering an account the first time a Google identity is seen or
linking it to an existing password account with the same, Google-verified, email address. See
`docs/GOOGLE_SIGN_IN.md` at the repo root for one-time Google Cloud setup — the endpoint answers
"not configured" until that's done, without affecting anything else.

`docs/API.md` walks through the flows.

`DELETE /api/v1/users/me` permanently deletes the signed-in lifter's account and everything it
owns — a genuine hard delete, the one exception to the tombstone model the rest of the API uses
for deletions (see `docs/ARCHITECTURE.md`). An access token is refused from the moment the account
it names is gone, checked on every request rather than revoked outright.

---

## API documentation

- Swagger UI at `/swagger` in development, with an **Authorize** box for the bearer token.
- OpenAPI document at `/openapi/v1.json`.
- `docs/API.md` for the parts a schema cannot express: the token flow, ownership, idempotency,
  optimistic concurrency, and how state restore is meant to be used.

Health checks:

| Endpoint | Checks |
|---|---|
| `/health/live` | The process is up. Deliberately does not touch the database. |
| `/health/ready` | PostgreSQL is reachable. |
| `/health` | Everything. |

---

## Deployment notes

Production shape, running on a shared OVH VPS (other projects live on the same box):

```
Internet ──► HTTPS ──► shared Caddy (:80/:443) ──► Griff Gym API (127.0.0.1:8080) ──► PostgreSQL
                              │
                              └──► other sites on the same box (untouched)
```

- Caddy is a single host-level instance shared by every site on the box — not something this
  project brings up itself. It terminates TLS and renews Let's Encrypt certificates for all of
  them. GriffGym's compose stack has no reverse proxy of its own: the API container publishes
  only to a loopback port, and `deploy/remote-setup.sh` adds a clearly-marked block for
  `API_DOMAIN` to the shared `/etc/caddy/Caddyfile`, validating the result before reloading so a
  bad config can't take down anyone else's site. See `docs/DEPLOYMENT.md`.
- PostgreSQL publishes no port to the host at all; only the API container can reach it.
- The API container runs as an unprivileged user (`$APP_UID`), not root.
- Migrations run as an explicit deployment step (the `migrate` Compose service, a separate build
  target that stays at the SDK layer), before the API container is (re)started — never on
  container startup. See `docker-compose.prod.yml`.
- `POSTGRES_PASSWORD` and `JWT_SIGNING_KEY` are generated once on first deploy and kept in
  `/opt/griffgym/.env` on the server, outside the directory that gets overwritten by every
  redeploy.
- No CORS is configured. The only client is a native Android app, which is not a browser and is
  not subject to the same-origin policy. A future web client is the moment to add it, with its
  own origin named explicitly — not `AllowAnyOrigin`.

### Deploying / updating

From the repository root (not this directory):

```bash
./deploy-backend.sh
```

Edit `deploy.config.sh` once first (server address, SSH user, `API_DOMAIN`, `ACME_EMAIL`) — see
`docs/DEPLOYMENT.md` for what it does and what's needed on the server side (a DNS A record for
`API_DOMAIN` pointing at the server, before the first run). Running it again ships whatever is
currently in `GriffGym.Backend/`: it syncs the source, rebuilds the image, applies pending
migrations, and restarts the stack — the same command for the first deploy and every one after.
