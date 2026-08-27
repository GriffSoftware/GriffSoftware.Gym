# Griff Gym — Test Plan

## How the suite runs

Every test runs on Robolectric under `./gradlew test`, including Compose UI tests and Room
migration tests. `androidTest` source sets declare an instrumentation runner but contain no
tests, so `./gradlew connectedAndroidTest` succeeds with zero tests and no device or emulator
is required to validate the app.

Last full run:

- `./gradlew test` — **304 tests, 0 failures**, split across `:domain` (42), `:application`
  (105), `:infrastructure` (110), `:presentation` (47).
- `./gradlew assembleDebug` and `./gradlew assembleRelease` — both succeed.
- `./gradlew connectedAndroidTest` — succeeds, 0 tests.

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
