# Griff Gym — Project Brief

## Product

**Griff Gym** is a native Android application designed for strength training and gym workout tracking.

The primary goal of the application is to make following a structured strength program and logging real workout results fast, reliable, and convenient.

The application is designed primarily for personal use but should be built with production-quality architecture that allows future development.

---

## Product principles

Griff Gym should be:

- fast,
- offline-first,
- reliable,
- simple during an active workout,
- visually distinctive,
- focused on strength training,
- safe for long-term workout history.

The application must remain usable without network access.

User workout data is considered important and must not be lost during normal application upgrades.

---

## Platform

Android only.

Technology:

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Room
- Hilt
- Kotlin Coroutines
- Flow
- StateFlow
- AndroidX Lifecycle
- Gradle Kotlin DSL
- KSP

The UI is implemented entirely using Jetpack Compose.

XML layouts are not part of the application architecture.

---

## Architecture

Griff Gym follows Clean Architecture.

Main modules:

```text
:app
:domain
:application
:infrastructure
:presentation