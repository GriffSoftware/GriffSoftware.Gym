# Griff Gym

Griff Gym is a native Android strength-training and workout logging application.

@docs/PROJECT_BRIEF.md

## Technology

- Kotlin
- Jetpack Compose
- Material 3
- Room
- Hilt
- Coroutines
- Flow / StateFlow
- Navigation Compose
- Gradle Kotlin DSL
- KSP

No XML layouts should be introduced unless explicitly requested.

## Architecture

The project follows Clean Architecture and is divided into:

- `:app`
- `:domain`
- `:application`
- `:infrastructure`
- `:presentation`

Dependency boundaries must be preserved.

Business logic belongs primarily in Domain or Application Use Cases.

ViewModels must not access Room DAOs directly.

Room entities must not leak into Presentation.

## Development workflow

Use the specialized project agents proactively.

### Android implementation

Use `senior-mobile-engineer` for:

- feature implementation,
- bug fixing,
- Android architecture,
- Compose,
- Room,
- refactoring,
- performance,
- developer unit tests.

### Quality assurance

After meaningful feature implementation or bug fixes, use `qa-engineer` to:

- review acceptance criteria,
- identify edge cases,
- assess regression risk,
- define or execute smoke scenarios,
- define regression scenarios.

### Documentation

After user-visible functionality or architectural/setup changes, use `technical-writer` to determine whether documentation requires updating.

Do not update documentation merely for internal implementation changes that do not affect developers or users.

## Quality expectations

Every meaningful production change should consider:

1. architecture,
2. unit tests,
3. persistence,
4. regressions,
5. UI consistency,
6. documentation impact.

Do not sacrifice correctness or maintainability for speed.

## Data safety

Workout history is user data and must be preserved.

Never use destructive Room migrations as a shortcut.

Database schema changes must consider existing installations.

## Testing

Run relevant tests after changes.

Bug fixes should include regression tests when practical.

Never claim tests passed unless they were actually executed.

## UI

Preserve the existing Griff Gym visual identity:

- dark industrial UI,
- yellow accent,
- strong typography,
- restrained Material styling,
- compact training-oriented layout.

Prefer existing reusable Griff Gym Compose components over introducing visually inconsistent alternatives.