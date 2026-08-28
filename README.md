# Griff Gym

An offline-first powerlifting log for Android, written in Kotlin with Jetpack Compose.
Every training feature works with no account and no network — the phone's Room database is
always the source of truth. An account is optional and adds a cloud backup on top of that;
it is a copy of the local data, never a replacement for it.

## Modules

The project follows Clean Architecture as real Gradle modules, not just packages:

```
:app             composition root — Application, MainActivity, Hilt entry points, launcher icon
:presentation    100% Compose UI, ViewModels, UiState/UiEvent, navigation, theme
:application     use cases; all business orchestration lives here, never in a ViewModel
:domain          pure Kotlin: models, value objects, business rules, repository contracts
:infrastructure  Room database, entities, DAOs, mappers, repository implementations, seeding
```

Dependencies point inward:

```
:app ──────────► :presentation ──► :application ──► :domain
  └────────────► :infrastructure ──────────────────► :domain
```

`:domain` and `:application` are plain JVM modules — they cannot reference the Android SDK,
Compose or Room even by accident. `:presentation` never sees `:infrastructure`; the two are
wired together only in `:app` through Hilt.

## Two modes

Griff Gym stores this choice once, in `UserMode`, and never re-asks:

- **Local-only.** Nothing leaves the phone. This is the default, and it is fully
  functional — an account is a backup, not a licence for the app.
- **Authenticated.** Signed in to a Griff Gym account. Room is still the only thing the UI
  reads from; the account gives that data a durable copy on the server.

## First run

The first screen a lifter sees is the data-protection choice: keep training local, or
create an account. An installation that already holds training data — someone upgrading
from before accounts existed — is shown this exact same screen once, because that lifter
has the most to lose and has never been asked. Choosing local changes nothing about their
data.

Once that is decided, a fresh install with no reference maxes yet opens onboarding instead
of Home: a welcome screen, then one step per lift (Squat, Bench Press, Deadlift) where the
lifter either enters a known 1RM or gets one from the same Epley calculator as the CALC
screen, then an editable summary. "BUILD MY PROGRAM" turns those three numbers into
Cycle 1: a personal six-week block sized off percentages of the lifter's own maxes —
nothing is generated for someone who has not entered a number.

Anyone with reference maxes, a program or history already on disk goes straight to Home;
onboarding only ever triggers on a genuinely empty install.

## The rule that shapes the data model

The training plan is a **template**. Pressing START copies it into a `workout_session`
snapshot: every prescribed set becomes its own `set_log` row carrying `plannedWeight`,
`plannedReps` and `plannedRpe`. Results are written to separate `actual*` columns.

Editing the program later therefore cannot rewrite history, and a session shows what was
asked for next to what actually happened.

## Persistence

The active workout is not held in a ViewModel. Every keystroke that parses is written
straight through to Room, so a session survives the app being killed between sets — Home
then shows `IN PROGRESS` and a `CONTINUE` button.

The exercise catalogue is seeded once on first launch, guarded by existence checks inside a
single transaction, so updating the app never duplicates it or touches logged history.
Reference maxes and the training program are no longer auto-seeded — they come from
onboarding, and are the lifter's own numbers.

## Program progression

The plan is a sequence, not a calendar. `program_progress` points at the current unit and
advances when a session is completed:

```
Week 1 Day I → Week 1 Day II → Week 1 Day III → Week 2 Day I → … → Week 6 Day III
```

Training a day early or a week late makes no difference. Week 6 is a deload, trained at
50% of that cycle's reference maxes.

Griff Gym runs as an ongoing sequence of these six-week Training Cycles rather than a
single fixed program. Finishing the last workout of week 6 opens a Cycle Review screen
summarising the cycle just completed, where the lifter chooses — independently per lift —
whether their reference max goes up by a suggested amount, stays the same, or changes by a
custom amount for the next cycle. Starting the next cycle snapshots the new reference
maxes into a fresh six-week block. A CYCLES screen, reachable from Home and from the
navigation drawer, shows the active cycle's progress and reference-max snapshot, compares
it against the previous cycle, and lists past cycles for read-only review.

## Estimated 1RM

Epley throughout: `1RM = weight × (1 + reps / 30)`, with a single returned untouched rather
than inflated. Statistics take the best estimate a session produced for each of the big
three. A reference max is a planning number the lifter typed in and is deliberately never
treated as a personal record.

## Cloud backup and restore

Signing in does not simply turn sync on — what happens depends on what already exists on
each side, and getting that wrong destroys training history. `ResolvePostSignInActionUseCase`
looks at both the phone and the account and picks one of four outcomes:

| Phone | Account | Result |
|---|---|---|
| empty | empty | start onboarding, nothing to move |
| has data | empty | upload the phone's history (the migration case) |
| empty | has data | restore the account's history to this phone |
| has data | has data | refuse to guess — ask the lifter to resolve it |

Once an account is backing up, logging a set writes to Room immediately and marks the
change `PENDING_UPLOAD`; a background sync (WorkManager, `NetworkType.CONNECTED`,
exponential backoff) pushes it when a connection is available. Nothing about a workout
ever waits on the network.

Two rules protect the data on both sides of that:

- A backup that fails is never recorded as one that succeeded. The app stays local-only and
  says so — it does not claim a copy exists that does not.
- If the server has moved on before a change lands, the record is marked `CONFLICT` and the
  local copy is kept untouched. Nothing is silently overwritten, and a conflicted record is
  not retried until it is resolved.

Restoring an account's history to a phone is one Room transaction: it lands whole, or the
database is left exactly as it was. Historical training cycles are restored exactly as
stored — never regenerated from today's reference maxes, which would quietly rewrite what a
lifter was actually asked to do on a day years ago.

Signing out revokes the token and clears this device's cached training data; it does not
touch the cloud copy and it is not the same as deleting the account.

## Profile and account deletion

The top-right avatar opens **Profile** for a signed-in lifter (and the sign-in/register screen
for anyone local-only) — cloud backup status, last backup time, sign out, and a fenced-off
**Danger Zone** with permanent account deletion.

Deleting an account is deliberately hard to do by accident: a first dialog lists exactly what
goes — the account, every training cycle, all workout history, any workout in progress, every
logged set, reference maxes, the cloud backup — and states that it cannot be undone; a second
requires typing `DELETE` before the button confirming it will even enable.

Nothing local is touched until the server confirms the account is gone. If the request fails —
offline, a server error, a timeout — the account, its tokens and the phone's Room database are
left exactly as they were, and the lifter is offered `TRY AGAIN`. There is no queued "delete
later once back online": a deletion nobody was there to see complete is not one anybody
consented to. On success the app cancels its background sync jobs, clears the local training
data, and returns to the first-run flow — the data-mode choice, then onboarding — as if the
account had never existed on this phone. There is no separate global exercise dictionary: the
movement catalogue is per account, so it is deleted along with everything else; a fresh start
recreates it from scratch during onboarding.

## Configuration

The API base URL is a build setting, not a constant in the source:

- **Debug** builds point at `http://10.0.2.2:8080/` (how the emulator reaches the host
  machine) and a debug-only network security config allows cleartext to that address and to
  `localhost`/`127.0.0.1` only — every other host still requires TLS.
- **Release** builds read `GRIFFGYM_API_BASE_URL` from a Gradle property or the environment
  and refuse to build if it is not an HTTPS URL.

```bash
./gradlew :app:assembleRelease -PGRIFFGYM_API_BASE_URL=https://api.example.com/
```

## Security posture

- The refresh token is encrypted with an AES-256-GCM key from the Android Keystore and kept
  in its own DataStore file — never plain text, never `SharedPreferences`. If the key ever
  becomes unreadable (factory reset, restoring a backup onto different hardware) the stored
  token is dropped and the lifter is asked to sign in again, rather than the app getting
  stuck.
- Access tokens are short-lived and refreshed automatically. Refresh tokens rotate on every
  use and refreshing is single-flight: several requests failing at once trigger exactly one
  refresh, because presenting a rotated token twice is treated by the server as theft, not a
  race.
- HTTP logging is `BASIC` and only in debug builds; the `Authorization` header is redacted
  even there.
- Passwords are function parameters and nothing else — never stored, never cached.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew test
```

Requires JDK 21 (the Gradle toolchain is pinned to it) and an Android SDK with API 36.

## Notes on the implementation

- `minSdk` is 26. `java.time` is then available natively and Compose can apply weight axes
  to the bundled variable fonts, which removes the need for core library desugaring.
- Archivo Narrow, Inter and JetBrains Mono are bundled under the SIL Open Font License so
  the app renders identically offline.
- Accessory work is seeded for weeks 1–5 only; the deload week deliberately drops it.
