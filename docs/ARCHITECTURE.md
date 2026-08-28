# Griff Gym — Architecture

This document describes what the code actually does, not what the module names imply. Where
the two disagree, the disagreement is called out rather than papered over.

## Modules and dependency direction

```
:app             composition root — Hilt entry points, MainActivity, Application
:presentation    Compose UI, ViewModels, navigation
:application     use cases — business orchestration
:domain          plain Kotlin models, value objects, repository interfaces
:infrastructure  Room, DataStore, Retrofit/OkHttp, WorkManager, repository implementations
```

```
:app ──────────► :presentation ──► :application ──► :domain
  └────────────► :infrastructure ──────────────────► :domain
```

`:domain` and `:application` are plain JVM modules and cannot depend on the Android SDK,
Compose or Room. `:presentation` does not depend on `:infrastructure`; the two are wired
together only in `:app`, through Hilt. ViewModels call use cases, not DAOs or repositories
directly, and Room entities never appear in a `UiState`.

## A request through the layers

```
Compose screen
      │  user action
      ▼
ViewModel                       (presentation)
      │  invoke(...)
      ▼
Use case                        (application)
      │  repository interface
      ▼
Repository implementation       (infrastructure)
      │  DAO / Retrofit call
      ▼
Room  ◄──────────────────────►  Sync engine
      │  Flow<Entity>
      ▼
ViewModel  ──►  UiState  ──►  Compose recomposes
```

The sync engine sits beside this path, not inside it. It reads and writes Room through its
own DAOs and never intercepts a use case call — a workout screen has no idea whether an
account exists.

## Two modes, one rule

`UserMode` (`domain/model/UserMode.kt`) is `Undecided`, `LocalOnly`, or
`Authenticated(userId, email)`. It is persisted in DataStore
(`infrastructure/preferences/DataStoreUserModeRepository.kt`) so the choice survives a
process death and is never re-asked.

The rule that makes this safe to build on: **Room is the only thing the UI reads, in both
modes.** Logging a set, completing a workout, starting a cycle — none of it touches the
network on the way to the screen. Authenticated mode adds a second consumer of Room's write
path (the sync metadata table) and a background process that empties it; it does not change
how a screen gets its data.

## Startup routing

`GetStartupDestinationUseCase` (`application/account/`) decides, once per process launch,
which of three subtrees `GriffGymRoot` (`presentation/navigation/GriffGymRoot.kt`) mounts:

1. **`ChooseDataMode`** — `UserMode` is still `Undecided`. Shown to a genuinely fresh
   install and, once, to an installation that predates accounts and already holds years of
   training — that lifter has the most to lose and has never been asked.
2. **`Onboarding`** — a data mode is chosen but there are no reference maxes yet.
3. **`Ready`** — the app itself.

`GriffGymRoot` swaps the whole subtree rather than branching inside one `NavHost`. Leaving a
flow discards its entire graph, which is what makes signing out safe: the app graph and
anything holding a reference to training data is torn down wholesale rather than left
dangling after that data is cleared.

The order of the two questions in the use case is deliberate: "has a data mode been chosen?"
is asked before "is onboarding done?", because the first question applies whether or not the
lifter has any data yet, and asking it first means an installation that predates accounts is
never dragged back through onboarding it already completed.

## Post-sign-in routing

Signing in does not imply "start syncing" — it implies "figure out which of four situations
this is", because getting it wrong destroys training history. `ResolvePostSignInActionUseCase`
(`application/account/`) reads whether the phone has training data and whether the account's
cloud state is empty, and returns exactly one of:

| Local data | Cloud data | Action |
|---|---|---|
| none | none | `StartOnboarding` |
| some | none | `BackUpLocalData` — the local-to-account migration |
| none | some | `RestoreCloudData` — the new-phone case |
| some | some | `ResolveConflict` — refuse to guess, ask the lifter |

`PostSignInRouter` (`presentation/account/`) turns that into one screen. This is a use case
with a name and its own tests specifically because an `if` inside a ViewModel is exactly the
kind of place a data-destroying mistake hides.

## The `CloudSnapshot` seam

`infrastructure/sync/model/CloudSnapshot.kt` defines a tree of plain data classes — one
lifter's entire training state, addressed by `syncId` rather than by Room's `Long` row id —
that sits between the network side and the database side of sync.

This exists because the two sides have genuinely different problems. The network side deals
in JSON, nullable fields and a server that identifies everything by string id. The database
side deals in `Long` primary keys, foreign keys, and an insert order that has to respect
them. `RetrofitCloudStateGateway` is the only class that knows both the HTTP shape and the
snapshot shape; `LocalStateReader` and `LocalStateWriter` only know Room and the snapshot.
Nothing forces the API's DTOs into Room, and nothing forces Room's entities onto the wire.

## Sync ids and why `Long` primary keys stayed

Room migration `MIGRATION_2_3` (`infrastructure/database/migration/Migration2To3.kt`) adds a
`syncId` (a UUID, generated in SQL with `randomblob()` so a migration on a multi-year
history does not walk every row in Kotlin) to every synchronised table, plus a new
`sync_metadata` table. It does not touch, rename or renumber the existing `Long` primary
keys — every foreign key and every DAO query is already built on them, and rewriting them
would be a much larger and riskier change for no benefit.

The two identities serve different purposes: the `Long` id is what Room and the rest of the
app already use to relate rows to each other locally; the `syncId` is what survives the
local database being rebuilt from scratch during a restore, and what the server uses to
recognise "this is the same record" independent of which device inserted it. `program_progress`
does not get a `syncId` — it is a pointer at the current unit of a program, not a record in
its own right, and the server carries it as a field on the training program instead.

`Migration2To3Test` builds a database with history under schema 2, runs the migration, and
asserts every row survives with a stable, unique `syncId`. Nothing is dropped and nothing is
recomputed.

## Sync metadata and its states

`sync_metadata` (`SyncMetadataEntity`) is one row per synchronised entity, keyed by
`(entityType, entityId)` where `entityId` is the `syncId`. Its `syncState` is one of:

- `PENDING_UPLOAD` — changed locally, not yet sent.
- `SYNCED` — the server has this exact version.
- `CONFLICT` — the server had a newer version when this device tried to send. Parked, not
  retried, until something resolves it.
- `FAILED` — the last attempt failed for a reason other than a conflict (network, server
  error). Retried on the next pass.

Writing a set, completing a workout, changing a reference max, or starting a cycle all call
`SyncEngine.markPending`, which upserts one row and does not touch the network — the write
path never waits on connectivity. A record already `CONFLICT` is never quietly downgraded
back to `PENDING_UPLOAD`, because that would resend the local version and discard whatever
the server holds.

## Sync engine and scheduling

`SyncEngine` (`infrastructure/sync/SyncEngine.kt`) does the actual movement of data:

- `pushPendingChanges()` — uploads everything not yet `SYNCED` (skipping `CONFLICT` rows),
  exercises first because a template or log referencing an unknown movement would otherwise
  look like a conflict rather than an ordering mistake.
- `backupEverything(onProgress)` — the local-to-account migration. Marks the local state
  `SYNCED` only after the upload has actually returned successfully; recording a backup that
  did not happen is treated as the worst possible failure mode, because the lifter would stop
  worrying about a copy that does not exist.
- `restoreEverything()` — fetches the account's snapshot and hands it to `LocalStateWriter`.

A process-wide `Mutex` (`syncLock`) ensures one sync pass runs at a time — the periodic
worker, the app opening, and a manual "sync now" can all fire within moments of each other,
and running two passes concurrently would duplicate uploads and race the metadata writes.

`WorkManagerSyncScheduler` schedules the work: a one-off request on demand
(`ExistingWorkPolicy.KEEP`, so a pass already running is never cancelled to start an
identical one) and a periodic pass every six hours as a safety net for a phone that spent
days without a connection. Both require `NetworkType.CONNECTED` and use exponential backoff.
`GriffGymSyncWorker` is idempotent by construction — it uploads by `syncId`, so WorkManager
re-running it after a process death writes the same rows again rather than duplicating them —
and does nothing at all for a local-only lifter.

## Conflict handling

A conflict is a version mismatch the server reports on upload, not something the client
detects by comparing timestamps. When that happens, `SyncEngine` marks the record `CONFLICT`
and stops: the local copy is never overwritten, the server's copy is never assumed to be
correct, and the record is not retried until a person resolves it. Network and
authentication failures are treated differently — they abort the rest of that sync pass
rather than being written off record-by-record, so a dropped connection does not get logged
as a hundred unrelated failures.

At the account level, "phone has data and account has data" is its own outcome
(`ResolveConflict`) precisely because the app cannot tell "this is the same history syncing
again" apart from "these are two unrelated histories" — merging or overwriting either one
automatically is a guess the app is not willing to make. `DataConflictScreen` requires a
second, explicit confirmation before "use cloud data" is allowed to replace what is on the
phone.

## The restore transaction

`LocalStateWriter.replaceLocalState` (`infrastructure/sync/LocalStateWriter.kt`) wraps the
whole restore in `database.withTransaction { ... }`. Restoring means deleting the local
tables and reinserting roughly a dozen of them in dependency order — exercises, then
reference maxes and cycles, then programs, weeks, workout templates and their sets, then
workout sessions and their logs — because SQLite only knows a row's id after it is inserted,
and children need their parents' ids to be wired up correctly. Wrapping all of it in one
transaction means a failure partway through leaves the database exactly as it was, rather
than a mix of cycles with no plans and sessions with no sets that the app would otherwise
render as if it were real history.

Cycles are restored with the reference maxes they were originally calculated from, not
recomputed from whatever the lifter's maxes are today — a restore reproduces history, it
does not reinterpret it.

## Token storage

Refresh tokens are the one secret Griff Gym stores, and they are handled by
`KeystoreSecureTokenStorage` (`infrastructure/security/`):

- Encrypted with an AES-256-GCM key generated in the Android Keystore, never in plain text
  and never in `SharedPreferences`. The deprecated `security-crypto` library is deliberately
  not used.
- Kept in its own DataStore file, separate from ordinary app preferences, so clearing
  settings can never take a session with it.
- If the ciphertext becomes permanently undecryptable — a factory reset, an OEM device
  re-enrollment, restoring an app backup onto different hardware — it is treated as "no
  session" rather than a crash. The lifter is asked to sign in again; nothing about their
  Room data is affected.

`TokenAuthenticator` (`infrastructure/network/auth/`) renews an expired access token behind
a `Mutex`, so several requests failing at once with a stale token trigger exactly one
refresh call — refresh tokens are single-use and rotate, so a second concurrent refresh would
present an already-retired token, which the server treats as theft and revokes the whole
session for. A refresh that fails on the network keeps the stored credentials, because no
signal is not the same as a dead session; a `401` from the refresh endpoint specifically
means the session really is gone, and only then are the tokens cleared and a
session-expired signal raised. `AuthSession`, the type visible above infrastructure, carries
only `userId` and `email` — no token ever leaves this layer.

## Account deletion

`DeleteAccountUseCase` (`application/account/`) is the one irreversible action in the app, and
its ordering follows from that:

1. Call the server (`AuthRepository.deleteAccount`) and go no further until it confirms the
   account is gone. A wipe attempted while offline would destroy the lifter's local training
   while leaving the account and its cloud copy untouched, with no screen left afterwards able
   to offer either back. A failure here changes nothing locally — there is no "delete once a
   connection returns", because an unattended deletion is not one anybody agreed to.
2. Only once the server has answered: cancel the scheduled sync workers, then clear the local
   training data. That order matters — clearing Room first would leave a window in which a
   background sync pass wakes up, reads a half-cleared database, and tries to reconcile it
   against an account that the server has already deleted.
3. Reset `UserMode` to `Undecided` and clear the onboarding-completed flag, returning the
   installation to the state it was in before the account existed. The next launch goes through
   `GetStartupDestinationUseCase` like a fresh install: `ChooseDataMode`, then `Onboarding`.

The last three steps are best-effort and cannot fail the operation as a whole — by the time they
run, the account is already gone server-side, so reporting failure would tell the lifter their
data survives when it does not.

`ProfileRoute` reaches this exactly the way `GriffGymRoot` reaches sign-out: through the host,
not the nav graph, because both endings invalidate everything mounted below the root.

The top bar's avatar (`AvatarDestinationViewModel`) routes on `UserMode` — `Authenticated` opens
Profile, anything else opens the existing account/sign-in screen — so `DELETE ACCOUNT` is only
ever reachable when there is an account to delete.

On the server side, `DELETE /api/v1/users/me` (`GriffGym.Backend/docs/API.md`) is a hard delete,
not the tombstone the rest of that API uses for synchronised records — see
`GriffGym.Backend/docs/ARCHITECTURE.md` for why account deletion is the deliberate exception to
that rule. An access token for a deleted account stops being accepted immediately, but this is a
per-request existence check on sign-in — `AccessTokenValidation` — not token revocation; the JWT
itself remains a validly-signed, unexpired token until it would have expired anyway.

## Configuration

The API base URL is a Gradle build setting (`infrastructure/build.gradle.kts`), not a
constant in source. Debug builds point at `http://10.0.2.2:8080/` with a debug-only network
security config permitting cleartext to `10.0.2.2`, `localhost` and `127.0.0.1` only.
Release builds read `GRIFFGYM_API_BASE_URL` from a Gradle property or the environment and
fail the build if it is not `https://` — there is no default a release build can ship with
by accident. HTTP logging is `HttpLoggingInterceptor.Level.BASIC` with the `Authorization`
header redacted, and is compiled out entirely for release.
