# Griff Gym

An offline-first powerlifting log for Android, written in Kotlin with Jetpack Compose.
No backend, no accounts, no network — everything lives in a local Room database.

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

## First run

A fresh install has no reference maxes yet, so it opens onboarding instead of Home: a
welcome screen, then one step per lift (Squat, Bench Press, Deadlift) where the lifter
either enters a known 1RM or gets one from the same Epley calculator as the CALC screen,
then an editable summary. "BUILD MY PROGRAM" turns those three numbers into Cycle 1: a
personal six-week block sized off percentages of the lifter's own maxes — nothing is
generated for someone who has not entered a number.

Anyone upgrading from before onboarding already has reference maxes, a program or history
on disk and goes straight to Home as before; onboarding only ever triggers on a genuinely
empty install.

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
