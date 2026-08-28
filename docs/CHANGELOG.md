# Changelog

Entries are grouped by development phase rather than by release version, since Griff Gym has
not shipped a versioned release yet. Each entry explains what changed and why it mattered,
not a line-by-line diff.

## Phase 3 — Profile and account deletion

A **Profile screen**, reached from the top bar's avatar for a signed-in lifter: cloud backup
status, last backup time, sign out, and a fenced-off Danger Zone for permanently deleting the
account. The avatar previously did nothing for a signed-in lifter — it now routes by `UserMode`,
so an account without one still opens the existing sign-in screen.

**Permanent account deletion**, gated by a two-step confirmation — an inventory of what is
removed, then a case-sensitive `DELETE` typed into a field before the button confirming it will
enable. The server call goes first: nothing local is touched until the account is confirmed gone,
and any failure — offline, a server error, a timeout — leaves the account, its tokens and the
local database exactly as they were, with no deletion queued for later. On success the app
cancels its sync workers, clears local training data, and returns to the first-run flow rather
than a login screen.

On the backend, a new `DELETE /api/v1/users/me` reads the account from the access token's `sub`
claim alone and removes it, and everything it owns, in one transaction: workout sessions, training
cycles (and their programs, weeks, templates and planned sets), the exercise catalogue, reference
maxes, and every refresh token — in that order, because the exercise catalogue cannot be removed
while a training cycle still references it. This is a genuine hard delete, not the tombstone the
rest of the sync model uses, because the point of the feature is that the data is actually gone.

**Access tokens now stop working the moment the account they name is deleted.** A new
`OnTokenValidated` check looks the account up on every authenticated request and also compares the
token's `sstamp` claim against the account's current one — the same claim the API already issued
for a future password-invalidation feature, now put to its first real use.

## Phase 2 — Cloud backup and synchronisation

Griff Gym gained an optional account. Local-only use is unchanged and remains the default:
every training feature still works with no network and no account, because an account is a
backup, not a licence.

**Two modes, chosen once.** A new `UserMode` (`Undecided`, `LocalOnly`, `Authenticated`) is
persisted in DataStore and never re-asked. An installation that predates accounts and
already holds training history is shown the choice once, with an explicit warning that
staying local means that history cannot be recovered if the device is lost — that lifter has
the most to lose and had never been asked.

**Registration, login and sign-out**, with a JWT access token and a rotating refresh token.
The refresh token is encrypted with an Android Keystore key and stored outside
`SharedPreferences`; access tokens refresh automatically behind a single-flight lock so a
run of concurrent 401s produces exactly one refresh call instead of one per request (refresh
tokens are single-use, so more than one would look like theft to the server and revoke the
session). Signing out revokes the token and clears this device's cached training data — it
does not touch the cloud copy.

**Sign-in now resolves what to do with existing data** instead of assuming: a new
`ResolvePostSignInActionUseCase` looks at whether the phone and the account each hold
training data and picks one of start onboarding, back up the phone's history, restore the
account's history, or — when both sides already hold data — refuse to guess and ask.

**Background sync.** Completing a workout, logging a set or changing a reference max marks
the affected rows `PENDING_UPLOAD` in a new `sync_metadata` table; a WorkManager job uploads
them once a connection is available, with exponential backoff, and runs again periodically
as a safety net. Nothing about logging a set waits on the network.

**Room migration 2 → 3** gives every synchronised table a stable `syncId` (a UUID generated
in SQL) and adds `sync_metadata`. Existing `Long` primary keys, and every installation's
history, are left exactly as they were — nothing is dropped or recomputed. Four tests
(`Migration2To3Test`) build a database with history under the old schema and confirm every
row survives the migration.

**Conflict and failure handling favour keeping data over losing it.** A failed backup is
never recorded as a successful one. A version conflict marks the record `CONFLICT` and keeps
the local copy rather than retrying or overwriting it. Restoring an account's history to a
phone happens in one Room transaction, so it lands whole or the database is left exactly as
it was — and cycles come back with the reference maxes they were originally calculated from,
never recomputed from today's numbers.

**Configuration.** The API base URL is now a build-time setting rather than a constant:
debug points at the emulator's host loopback with a debug-only cleartext exception, and
release requires an HTTPS `GRIFFGYM_API_BASE_URL` or the build fails outright.

## Phase 1 — Onboarding and training cycles

The first-run experience and the ongoing training-cycle model: a three-lift onboarding flow
that builds a personal six-week program from the lifter's own numbers, program progression
through a sequence of workouts rather than a calendar, and a Cycle Review flow for rolling
into the next six-week block.

## Initial version

Skeleton and Clean Architecture module layout (`:app`, `:presentation`, `:application`,
`:domain`, `:infrastructure`), the workout-logging data model, and offline-first Room
persistence.
