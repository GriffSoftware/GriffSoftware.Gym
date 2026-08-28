# Griff Gym — Test Plan

## How the suite runs

Every test runs on Robolectric under `./gradlew test`, including Compose UI tests and Room
migration tests. `androidTest` source sets declare an instrumentation runner but contain no
tests, so `./gradlew connectedAndroidTest` succeeds with zero tests and no device or emulator
is required to validate the app.

Last full run:

- `./gradlew test` — **378 tests, 0 failures, 0 skipped**, split across `:domain` (42),
  `:application` (114), `:infrastructure` (121), `:presentation` (101).
- `./gradlew assembleDebug` and `./gradlew assembleRelease` — both succeed.
- `./gradlew connectedAndroidTest` — succeeds, 0 tests.

The backend (`GriffGym.Backend/`) is a separate .NET solution and is not built by the Gradle
run above. Its own suite, run independently:

- `dotnet test` (xUnit v3, run per-project) — **239 tests, 0 failures**, split across
  `GriffGym.Domain.Tests` (88), `GriffGym.Application.Tests` (58), `GriffGym.Infrastructure.Tests`
  (25, against a real PostgreSQL instance) and `GriffGym.Api.IntegrationTests` (68, a real
  ASP.NET Core host over a real PostgreSQL instance). The infrastructure and API suites need a
  reachable PostgreSQL — via `GRIFFGYM_TEST_POSTGRES`, a Docker daemon, or a local server — and
  skip with a reason rather than failing red when none is available.

## Automated coverage — Phase 2 (cloud backup and sync)

| Suite | Count | Covers |
|---|---|---|
| `Migration2To3Test` | 4 | Schema 2 → 3: `syncId` added to every synchronised table without dropping or renumbering existing rows; `sync_metadata` created empty. |
| `CloudStateRoundTripTest` | 10 | `CloudSnapshot` mapping in both directions between Room entities and the sync model. |
| `CloudBackupRestoreE2ETest` | 8 | Real Room database + real Retrofit/OkHttp over a loopback socket + real JSON: backup → wipe the local database → restore, asserting the data matches. |
| `SyncEngineTest` | 10 | `pushPendingChanges`, `backupEverything`, `restoreEverything`; conflict marking, failure handling, and that a failed backup is never recorded as synced. |
| `GriffGymSyncWorkerTest` | 5 | Worker success/retry/failure mapping; no-op for a local-only user. |
| `TokenAuthenticatorTest` | 6 | Token refresh, including the concurrent-401 single-flight case, and that a network failure during refresh does not clear stored credentials. |
| `AuthRepositoryTest` | 11 | Register, login, logout, session restore. |
| `ApiErrorMapperTest` | 15 | HTTP and network failures mapped to `GriffGymError`, including the version-conflict and unauthorized cases the sync engine and authenticator branch on. |
| Compose tests | — | Data protection, login, register, account and data-conflict screens. |

Existing domain, application, infrastructure and presentation suites from before Phase 2 are
unchanged in behaviour and continue to pass; the counts above are the totals including them.

## Automated coverage — Phase 3 (profile and permanent account deletion)

### Android

| Suite | Count | Covers |
|---|---|---|
| `ProfileViewModelTest` | 21 | Sync-now, sign-out (identical `LogoutUseCase` call as the account screen), the two-stage delete flow end to end, the confirmation-phrase gate, double-tap protection on both sign-out and delete (including the race where the first call finishes before the second tap arrives), retry-after-failure, dismiss being ignored while a deletion is in flight or after one has succeeded. |
| `ProfileScreenTest` | 15 | Backup status/last-sync rendering, the danger zone copy, that DELETE ACCOUNT only ever opens the explanation dialog, that the confirm button stays dead for anything but the exact typed phrase, that CANCEL stays reachable independently of the phrase, and the wording shown after a failure (retryable vs. session-expired). |
| `AvatarDestinationViewModelTest` | 5 | The avatar resolves to Profile for an authenticated user and to Account otherwise, the safe `Routes.ACCOUNT` default before the first emission, and that signing in moves the destination without remounting the app shell. |
| `DeleteDialogFitsShortScreensTest` | 2 | The explanation dialog's seven-item removal list and the confirmation dialog's actions both stay reachable (not clipped) on a short-screen layout, now that `AccountDialogSurface` scrolls. |
| `DeleteAccountUseCaseTest` (`:application`) | 7 | The ordering guarantee (server → cancel sync → wipe Room → clear mode → clear onboarding), that a failed or offline server call leaves the phone completely untouched, that an unrefreshable session deletes nothing and does not sign anyone out, that a local cleanup step throwing does not strand the app in a deleted-but-still-"Authenticated" state, and that the real startup resolver sends the next launch to the entry screen and then to first-run setup. |
| `AccountDeletionRepositoryTest` (`:infrastructure`) | 7 | The bearer `DELETE` call, credentials cleared only on success, credentials left alone on a server error/down service/no connection/dropped connection, and the two token-refresh cases (transparent refresh-then-delete, and a refresh failure reported as `Unauthorized` with nothing deleted). |

`AccountScreenTest`, `DataConflictScreenTest` and `DataProtectionScreenTest` (already in the
Phase 2 totals) continue to pass unchanged and exercise the other three dialogs that share the
now-scrollable `AccountDialogSurface`, but none of them assert reachability at a short screen
height the way `DeleteDialogFitsShortScreensTest` does for Profile — see the regression note
below.

### Backend

| Suite | Count | Covers |
|---|---|---|
| `AccountDeletionTests` (API integration) | 9 | `DELETE /users/me` returns `204` with no body; every row the lifter owned — refresh tokens, reference maxes, exercises, cycles and their whole plan tree, workout sessions and their logs — is gone, counted directly out of PostgreSQL and without the soft-delete filter; a second account is provably untouched, including that it can still call `GET /state` afterwards; old credentials cannot sign back in; the freed email address registers as a brand-new, empty account; a refresh token and an access token from a deleted account are both refused; every device's session is refused, not just the caller's; repeating the delete is refused (`401`) rather than run twice; an unauthenticated caller cannot delete anything; and — the one this feature is really about — the still-unexpired access token used to make the delete call itself stops being accepted on `/users/me`, `/state`, `/cycles`, `/workouts`, `/workouts/active`, `/reference-maxes` and `/exercises` immediately afterward. |
| `AccountDeletionUseCaseTests` (application) | 4 | The use case only ever acts on `ICurrentUser`'s id, never a parameter; another account's data is untouched; an unauthenticated call is refused before touching anything; a second deletion of an already-gone account is not an error and reports zero rows removed. |
| `AccountDeletionTransactionTests` (infrastructure, real PostgreSQL) | 3 | A failure part-way through the ordered deletion rolls every already-deleted row back — proving the transaction, not a fake, actually protects atomicity; the full ordered deletion empties every owned table; deleting the movement catalogue before the plans that reference it is refused by PostgreSQL's own `RESTRICT` constraint (23503), which is the reason the deletion order exists at all rather than a comment somebody could later "simplify" away. |

The new per-request account/security-stamp check (`AccessTokenValidation.EnsureAccountIsStillActiveAsync`,
wired into every `[Authorize]` endpoint via `OnTokenValidated`) is exercised indirectly by
every existing authenticated-endpoint test in the suite, since it now runs on all of them, and
directly by the theory case above. No existing test needed to change for it to pass, which is
itself evidence it does not alter behaviour for a token whose account still exists and whose
stamp still matches.

### Regression note: a shared dialog component changed

`AccountDialogSurface` and `StackedDialogActions` in `AccountComponents.kt` are shared by every
dialog in the account flow, not written new for Profile. Two changes landed with this feature:

- `AccountDialogSurface` now wraps its content in `verticalScroll`, fixing the account-deletion
  explanation dialog (seven removal bullets) on short screens or at large font scales.
- `StackedDialogActions` gained an independent `secondaryEnabled` parameter, defaulting to the
  existing `enabled` value — so every call site that does not pass it (every one outside the
  two new delete dialogs) is unaffected by construction.

The `secondaryEnabled` default makes the second change safe by inspection. The first is lower
risk than it looks (a `Column` that fits does not visibly scroll), but it now applies to four
dialogs this feature did not touch: the sign-out confirmation on the Account screen, the
data-conflict dialog, and both dialogs on the data-protection screen. None of those have a
short-screen or large-font-scale reachability test of their own. **Not automated; verify
manually** (see regression checklist below).

## Manual smoke scenarios

These exercise flows that automated tests cover in isolation but that only make sense
end-to-end on a device or emulator against a running backend.

1. **First run, stays local.** Fresh install → data-protection screen → choose local →
   onboarding → Home. No network call is made. Force-closing and reopening the app does not
   show the data-protection screen again.

2. **First run, creates an account.** Fresh install → data-protection screen → register →
   `PostSignInRouter` resolves `StartOnboarding` (both sides empty) → onboarding → Home,
   with the cloud status showing synced once the first workout is logged and a connection is
   available.

3. **Existing local user upgrades and adds an account.** Install with existing reference
   maxes, a program and logged history → the data-protection screen appears once, with the
   explicit "cannot be recovered" warning on the local-only path → choose to create an
   account instead → `PostSignInRouter` resolves `BackUpLocalData` → the existing history
   uploads and Home is unchanged. Verify nothing in the local database changed as a result of
   choosing to back up.

4. **New phone restore.** Sign in on a second, empty device with an account that already has
   backed-up data → `PostSignInRouter` resolves `RestoreCloudData` → history lands, including
   past cycles with their original reference-max snapshots (not today's maxes) → Home shows
   the same `IN PROGRESS`/next-workout state as the original device, or Home if nothing was
   in progress.

5. **Offline workout, then reconnect.** Put the device in airplane mode, log a full workout
   while signed in → cloud status shows pending/offline, the workout is fully usable
   throughout → reconnect → background sync uploads the workout without user action and the
   status moves to synced.

6. **Sign out, then sign back in.** While signed in with synced data, sign out → local
   training data is cleared from the device and the data-protection screen reappears → sign
   back in with the same account → `PostSignInRouter` resolves `RestoreCloudData` and the
   history reappears unchanged. Confirm the account still has its data even before signing
   back in (sign-out must not have touched the server copy).

7. **Two histories on sign-in.** Sign in on a device that already has local training history
   with an account that also already has backed-up history (e.g. reinstalling on a device
   that was previously used with a different local history) → `PostSignInRouter` resolves
   `ResolveConflict` → the data-conflict screen appears and "use cloud data" requires a
   second explicit confirmation before anything local is replaced.

8. **The avatar goes live.** From Home, tap the avatar while signed in → Profile opens; tap it
   again while already on Profile → no second copy is pushed (`currentRoute != avatarDestination`
   guards it) → back returns to Home. Sign out from the Account screen (not Profile), then tap
   the avatar → Account opens instead. Repeat the first check from mid-workout (`Routes.LOG`)
   specifically: tap the avatar during an active set, land on Profile, press back, and confirm
   the workout — including any rest timer that was running and any set field mid-edit — is
   exactly as it was. This exact path is not covered by any automated test.

9. **Sign out from Profile vs. from Account.** Confirm sign-out reads and behaves identically
   from both entry points: same confirmation copy, same result (local training cleared, the
   data-protection screen reappears, the server copy is untouched). They share one
   `LogoutUseCase`, so a difference here would be a regression in the screen, not the use case.

10. **Delete an account, end to end, against the real backend.** Sign in on a device with real
    training history and a completed sync → Profile → DELETE ACCOUNT → read the explanation →
    CONTINUE → type `delete` in lower case and confirm the button stays dead → type `DELETE` →
    DELETE MY ACCOUNT → the app returns to the data-protection screen → choosing local or
    signing in again leads into first-run setup, not an empty Home. Separately confirm the old
    credentials can no longer sign in, and that another account signed in elsewhere is
    unaffected — the same properties `AccountDeletionTests` proves against raw PostgreSQL, now
    proved once through the app's own UI.

11. **Delete while offline.** Airplane mode → Profile → DELETE ACCOUNT → CONTINUE → type
    `DELETE` → DELETE MY ACCOUNT → the failure dialog appears saying nothing was removed → turn
    the connection back on → TRY AGAIN succeeds. Confirm at every step before the final retry
    that the account, the session and the local training data are all still present.

12. **Kill the process mid-deletion.** Start a deletion against a real, reachable backend, and
    force-stop the app (or pull the process) in the moment between the confirm tap and the
    dialog closing — timing this by eye against a slow/throttled connection is the practical
    way to hit the window on a device. Reopen the app. **Expected: the entry screen, never
    Home.** The cleanup order was changed so that the stored identity is cleared before the
    database (see "Fixed since this pass"), which means no partial state can come back as a
    signed-in lifter. Training data may still be on the device if the wipe did not finish;
    that is the accepted residue — the lifter's own data, on the lifter's own phone.

13. **A periodic sync in flight during deletion.** With a large-enough pending change set that
    a background sync is genuinely still writing to Room (e.g. throttle the connection right
    after logging several sets, so the periodic/one-off sync worker is mid-pass), open Profile
    and delete the account. Afterward, inspect the device's database for any row belonging to
    the deleted account. **Expected: none.** The wipe now takes the sync engine's own lock, so
    it waits for a pass in flight and no pass can start until it has committed. Still worth
    doing on a device once, because the automated cover for it holds the lock synthetically
    rather than through a real WorkManager worker.

14. **Shared dialog surface, other screens.** With a short device (or a phone in landscape) and
    the system font scale turned up, open the sign-out dialog on the Account screen, the
    data-conflict dialog, and both data-protection dialogs, and confirm every button on each is
    still reachable. These share the `AccountDialogSurface`/`StackedDialogActions` components
    this feature changed, but only Profile's two delete dialogs have an automated test for it.

## Regression checklist — every request now does a DB lookup

`AccessTokenValidation.EnsureAccountIsStillActiveAsync` runs on **every** `[Authorize]`
endpoint, not just `DELETE /users/me`. It is a new failure mode (a `401` where there used to be
none) sitting in front of the whole authenticated surface, so a change this size earns a pass
over the rest of auth even though the diff did not touch it:

- [ ] Register → the returned access token immediately calls `/state` successfully (verified
      by `AccountDeletionTests`' sibling suites and by scenario 2 above; re-run after any change
      near `RegisterUserUseCase` or the JWT bearer configuration).
- [ ] Login, and Google sign-in, the same.
- [ ] Refresh rotation still works for a token issued *before* this feature shipped — i.e. a
      token whose `sstamp` claim matches the current stamp continues to be accepted, and the
      whole refresh/rotate/reuse-detection path (`RefreshTokenUseCase`) is unaffected, since
      `/auth/refresh` is `[AllowAnonymous]` and never goes through the new check itself.
- [ ] `logout-all` (the one `[Authorize]` endpoint in `AuthController`) still works normally for
      a live account, and now correctly 401s if called with a token from an already-deleted one.
- [ ] The full sync/backup/restore path (`/state`, cycles, workouts, reference-maxes,
      exercises) still succeeds end-to-end for a normal signed-in lifter — the new check reads
      one column via `FindSecurityStampAsync` on every call, so this is as much a latency sanity
      check as a correctness one.
- [ ] Startup for a signed-in lifter with an expired-but-refreshable session, and for one whose
      refresh has genuinely failed, are unaffected: both are pre-existing paths and neither
      goes anywhere near the new lookup until a request actually reaches the API.

None of the above regressed in the runs performed for this pass (`dotnet test`, all 239 green,
including the full existing `Auth`, sync-engine and API integration suites) — recorded here as
**verified**, not merely assumed, but worth re-checking explicitly the next time auth changes.

## Fixed since this pass

`qa-engineer` found two real, narrow-window defects during this review. Both were fixed rather
than documented and left, because both put a deleted account's data at risk — which is the one
thing this feature exists to prevent. Each fix is covered by a test that was confirmed to fail
without it:

- **A process death between the `204` and the end of local cleanup could bring the app back
  signed in to a deleted account.** The four cleanup steps span WorkManager, DataStore and Room
  and cannot be one transaction, so `DeleteAccountUseCase.forgetAccountLocally` now clears the
  stored identity *before* the database: `cancelScheduledSync` → `clearAccount` →
  `clearOnboardingCompleted` → `clearLocalAccountData`. Every prefix of that now resolves to the
  entry screen instead of to Home over a deleted account's training. Covered by
  `DeleteAccountUseCaseTest` ("the stored identity is cleared before the database…") and by
  `ProfileViewModelTest` ("the workers stop and the identity is forgotten…").
- **Cancelling the sync workers did not actually stop a pass already running.** WorkManager
  cancellation is asynchronous and cooperative, so a pass mid-flight could commit after the wipe
  had. `CloudBackupRepositoryImpl.clearLocalAccountData` now runs inside
  `SyncEngine.withSyncHeldOff`, taking the same mutex the passes take: the wipe waits for a pass
  in flight, and no pass can begin until the wipe has committed. Covered by
  `WipeHeldOffAgainstSyncTest`, which was confirmed to fail with the lock removed.

A third defect found in the same review — CANCEL being disabled alongside the confirm button in
the type-`DELETE` dialog, and both dialogs' buttons falling off a short screen — was fixed in
`StackedDialogActions` (a separate `secondaryEnabled`) and `AccountDialogSurface` (which now
scrolls). Covered by `ProfileScreenTest` and `DeleteDialogFitsShortScreensTest`.

## Known gaps

The two design-level races found in this review have been fixed and are described under "Fixed
since this pass". What remains genuinely untested is the behaviour of those fixes *on a real
device*, plus the scenarios below. Recorded here as **not tested**, not as **working**:

- **The two fixes above, end to end on hardware.** Both are covered by tests that fail without
  them, but the automated cover holds the sync lock synthetically and reasons about the crash
  window by ordering rather than by actually killing a process. Scenarios 12 and 13 are how to
  confirm them for real.
- **Delete end to end through the UI against a live backend.** Every property is covered in
  isolation — raw Postgres row counts in `AccountDeletionTests`, the Android suites against a
  MockWebServer — but the whole path has never been walked once on a device.
- **The avatar tapped during an active workout** with a running rest timer or an uncommitted
  text field. Architecturally sound, automated by nothing.
- **The four non-Profile dialogs** sharing the now-scrolling `AccountDialogSurface`, at a large
  font scale or on a small device (scenario 14).

The original write-ups of the two fixed races are kept below, because the reasoning is what
makes the fixes reviewable:

- **A process death between the server's `204` and the local cleanup finishing.**
  `DeleteAccountUseCase` clears the stored tokens as part of the API call succeeding, then runs
  four more independent steps (`cancelScheduledSync`, `clearLocalAccountData`, `clearAccount`,
  `clearOnboardingCompleted`) with no all-or-nothing guarantee across the four. If the process
  dies after the tokens are gone but before `clearAccount()` runs, the next launch still reads
  `UserMode.Authenticated` with a completed onboarding flag and an un-wiped Room database —
  `GetStartupDestinationUseCase` sends it straight to `Ready`, and Home shows the deleted
  account's training data exactly as it looked before deletion, with no on-screen indication
  the account is gone. This is not distinguishable, from the app's own logic, from an ordinary
  expired session — `UserModeRepository` deliberately does not infer mode from token presence —
  and nothing in the app currently observes `AuthRepository.observeSessionExpired()` to force a
  re-check. Scenario 12 above is how to attempt this on a device.
- **A periodic or one-off sync racing the wipe.** `cancelScheduledSync()` calls
  `WorkManager.cancelUniqueWork` twice and returns without waiting for either `Operation` to
  complete, even though `CloudSyncStatusRepository.cancelScheduledSync`'s own doc comment states
  its purpose is exactly to prevent a pass from reading a half-cleared database. A sync already
  mid-transaction at the moment deletion starts can still commit a write to Room after
  `clearLocalAccountData()`'s wipe transaction has already run, leaving a small amount of the
  deleted account's data behind on the device. Scenario 13 above is how to attempt this.

Both have since been fixed; see "Fixed since this pass" above for what changed and which test
holds each fix in place.
