# Griff Gym API

Everything is under `/api/v1`, speaks JSON, and returns RFC 9457 `ProblemDetails` on error.
Enums are serialised as names (`"TOP"` → `"Top"`, `"IN_PROGRESS"` → `"InProgress"`), timestamps as
ISO-8601 UTC, and dates as `YYYY-MM-DD`.

The generated schema lives at `/openapi/v1.json`, with Swagger UI at `/swagger` in development.
This document covers what a schema cannot express: the flows, and the rules a client has to know
to use the API correctly.

---

## Endpoints

### Auth

| | | |
|---|---|---|
| `POST` | `/api/v1/auth/register` | 201 with a token pair |
| `POST` | `/api/v1/auth/login` | 200 with a token pair |
| `POST` | `/api/v1/auth/refresh` | 200 with a **new** token pair |
| `POST` | `/api/v1/auth/logout` | 204, revokes one refresh token |
| `POST` | `/api/v1/auth/logout-all` | 204, revokes every session (requires a bearer token) |

### User

| | | |
|---|---|---|
| `GET` | `/api/v1/users/me` | The signed-in lifter |

### Reference maxes

| | | |
|---|---|---|
| `GET` | `/api/v1/reference-maxes` | All three, if set |
| `PUT` | `/api/v1/reference-maxes/{lift}` | `lift` is `Squat`, `BenchPress` or `Deadlift` |

### Exercises

| | | |
|---|---|---|
| `GET` | `/api/v1/exercises` | The lifter's movement catalogue |

Read-only. Entries are created as a side effect of uploading a cycle, which carries the movements
its plan refers to.

### Cycles

| | | |
|---|---|---|
| `GET` | `/api/v1/cycles` | Summaries with progress, newest first |
| `GET` | `/api/v1/cycles/{cycleId}` | One cycle with its full plan |
| `POST` | `/api/v1/cycles` | 201, or 200 on an idempotent replay |
| `POST` | `/api/v1/cycles/{cycleId}/complete` | Closes it and clears the progress pointer |
| `PUT` | `/api/v1/cycles/{cycleId}/progress` | Moves the pointer to the next unit |

### Workouts

| | | |
|---|---|---|
| `GET` | `/api/v1/workouts` | Paginated history, newest first |
| `GET` | `/api/v1/workouts/active` | 200, or **204** when nothing is running |
| `GET` | `/api/v1/workouts/{sessionId}` | One session with every set |
| `POST` | `/api/v1/workouts` | 201, or 200 on an idempotent replay |
| `PUT` | `/api/v1/workouts/{sessionId}` | Replaces notes and the logged tree |
| `PUT` | `/api/v1/workouts/{sessionId}/sets/{setId}` | Logs one set |
| `POST` | `/api/v1/workouts/{sessionId}/complete` | Freezes tonnage, makes it read-only |
| `POST` | `/api/v1/workouts/{sessionId}/cancel` | Finishes it without completing it |

Query parameters on the list: `page`, `pageSize` (max 100), `cycleId`, `status`, `from`, `to`.

### State and system

| | | |
|---|---|---|
| `GET` | `/api/v1/state` | Everything needed to rebuild an installation |
| `GET` | `/health/live` | Process is up; does not touch the database |
| `GET` | `/health/ready` | PostgreSQL is reachable |
| `GET` | `/health` | Everything |

---

## Authentication flow

```
POST /api/v1/auth/register  ──►  { accessToken, refreshToken, expiresInSeconds, ... }
```

Send the access token on every other request:

```
Authorization: Bearer <accessToken>
```

It lives about fifteen minutes. When it expires — or a moment before, using `expiresInSeconds` —
exchange the refresh token:

```
POST /api/v1/auth/refresh   { "refreshToken": "..." }   ──►  a NEW pair
```

### Rotation, and what a client must do about it

Refresh tokens are **single use**. Every refresh mints a new one and retires the one presented.

> **Store the new refresh token before you do anything else with the response.** A client that
> keeps the old one will be locked out on its next refresh — and, worse, will trip the reuse
> alarm and sign the lifter out on every device.

Presenting an already-rotated token is treated as theft, not as a retry: two parties holding the
same secret means one of them should not have it, and which one is unknowable. Every session for
that account is revoked and the lifter has to sign in again everywhere.

`401` from `/refresh` means exactly one thing: send the lifter to the login screen.

### Devices

`deviceId` is an optional opaque label on register, login and refresh. It exists so a lifter can
hold several live sessions at once — phone, old phone, tablet — and so a future "your sessions"
screen can name them. It is never treated as a credential or as proof of anything.

`POST /api/v1/auth/logout-all` ends every one of them.

---

## Ownership

**Nothing in this API takes a user id.** Not in a route, not in a query string, not in a body.
Whose data a request touches is derived from the signed access token, so forging identity means
forging a signature.

A record belonging to another account returns **404, not 403**. That is deliberate: answering
"forbidden" would confirm the identifier exists, and a list of GUIDs would become a way to
enumerate other people's data. Absence and denial look identical from outside.

---

## Idempotency

`POST /api/v1/cycles` and `POST /api/v1/workouts` both accept the `id` the client generated.

Send the same request twice and the second one returns **200 with the record that already
exists**, not 201 and a duplicate. A phone that retried after a timeout is the normal case for an
offline-first app, not an error.

```
POST /workouts  { "id": "ABC", ... }  ──► 201 Created   workout ABC
POST /workouts  { "id": "ABC", ... }  ──► 200 OK        workout ABC   (not a second workout)
```

If the identifier is taken by a record belonging to somebody else, the answer is `409` — and it
says only that the id is in use, never whose.

---

## Optimistic concurrency

Every record carries a `version`. Send back the one you hold:

```json
{ "expectedVersion": 4, "weightKg": 190, "reps": 3, "rpe": 8, "completed": true }
```

If another device wrote first, the answer is `409` with both numbers:

```json
{
  "type": "https://httpstatuses.io/409",
  "title": "Version conflict",
  "status": 409,
  "detail": "Workout has moved on: expected version 4, found 6.",
  "expectedVersion": 4,
  "actualVersion": 6
}
```

Re-read the record, merge, and write again with the version you were told about.

**Every write response returns the new `version`.** Use that value on the next write; do not
increment it yourself.

Omitting `expectedVersion` is last-write-wins. That is only right for a device that knows it is
the only one writing.

---

## Plan versus actual

The rule the whole model rests on: what was prescribed and what happened are separate fields, and
one never overwrites the other.

Starting a workout snapshots the planned unit. Every prescribed set becomes a logged set carrying
its `planned*` values, with the `actual*` values empty:

```json
{
  "id": "…",
  "position": 1,
  "plannedWeightKg": 192.5, "plannedReps": 3, "plannedRpeMin": 8, "plannedRpeMax": 8,
  "actualWeightKg": null,   "actualReps": null, "actualRpe": null,
  "completed": false
}
```

After the set is logged:

```json
{
  "plannedWeightKg": 192.5, "plannedReps": 3, "plannedRpeMin": 8, "plannedRpeMax": 8,
  "actualWeightKg": 190,    "actualReps": 3,  "actualRpe": 8.5,
  "completed": true,
  "volumeKg": 570, "estimatedOneRepMaxKg": 209
}
```

Editing the plan later cannot rewrite history, and a client can always show what was asked for
next to what was done.

A set cannot be marked `completed` without a weight and at least one rep — otherwise "completed"
would come to mean "completed, contents unknown" in a log meant to last years.

---

## Starting a cycle

One request carries the whole thing: the planning numbers, the movements the plan refers to, and
all six weeks. The phone generates the block locally, so there is nothing to negotiate.

```jsonc
POST /api/v1/cycles
{
  "id": "…",                       // optional; the phone's own identifier
  "cycleNumber": 3,
  "squatReferenceMaxKg": 210,
  "benchPressReferenceMaxKg": 170,
  "deadliftReferenceMaxKg": 225,
  "startedAtUtc": "2026-03-02T18:00:00Z",
  "exercises": [
    { "id": "…", "name": "Przysiad", "category": "Squat" }
  ],
  "program": {
    "name": "Blok IV — Siła",
    "weeks": [
      {
        "weekNumber": 1, "label": "ACCUMULATION", "type": "Training",
        "workouts": [
          {
            "dayNumber": 1, "sequenceNumber": 1, "title": "Squat Focus / Bench Volume",
            "exercises": [
              {
                "position": 1, "exerciseId": "…", "type": "Top",
                "plannedSets": [
                  { "position": 1, "weightKg": 187.5, "reps": 3, "rpeMin": 8, "rpeMax": 8 }
                ]
              }
            ]
          }
        ]
      }
      // … weeks 2–6, week 6 being "type": "Deload"
    ]
  }
}
```

Written in a single transaction — cycle, catalogue, program, weeks, workouts and every planned
set — so a failure cannot leave a lifter with half a plan.

The `exercises` array is not optional ceremony: templates reference movements by id, and carrying
them on the plan means persisting a program never depends on a catalogue having been seeded first.

Cycle numbers are unique per lifter and never reused; a duplicate is `409`.

The response's `program.currentWorkoutTemplateId` points at the first unit. The plan is a
**sequence, not a calendar** — training a day early or a week late changes nothing about what
comes next. Advance it with `PUT /api/v1/cycles/{cycleId}/progress` after each completed session.

---

## Two ways to create a workout

**Starting a planned one** — send the cycle and the template, and the server snapshots it:

```json
POST /api/v1/workouts
{ "trainingCycleId": "…", "workoutTemplateId": "…" }
```

**Uploading one you already have** — send its contents. Used for a session started offline, and
for backfilling history after creating an account:

```json
POST /api/v1/workouts
{
  "id": "…",
  "status": "Completed",
  "performedOn": "2025-11-04",
  "startedAtUtc": "2025-11-04T17:00:00Z",
  "finishedAtUtc": "2025-11-04T18:20:00Z",
  "weekNumber": 3, "dayNumber": 2, "title": "Deadlift Focus / Bench Light",
  "exercises": [
    {
      "position": 1, "exerciseName": "Martwy ciąg",
      "exerciseCategory": "Deadlift", "type": "Top",
      "sets": [
        { "position": 1, "plannedWeightKg": 200, "plannedReps": 3,
          "actualWeightKg": 202.5, "actualReps": 3, "actualRpe": 8.5, "completed": true }
      ]
    }
  ]
}
```

Sending neither is a `400`. Starting a second workout while one is `InProgress` is a `409` —
"which workout am I in?" has to have one answer.

Identifiers the client supplies are the identifiers the server keeps, all the way down to
individual sets.

---

## State restore

```
GET /api/v1/state
```

Everything one lifter's installation is made of, in one read-only document. This is the answer to
"my phone is in a river": a fresh install signs in, asks once, and rebuilds its local database.

```jsonc
{
  "schemaVersion": 1,
  "generatedAtUtc": "2026-03-02T19:14:22Z",
  "syncVersion": 4812,
  "profile":        { "id": "…", "email": "…" },
  "referenceMaxes": [ /* Squat, BenchPress, Deadlift */ ],
  "exercises":      [ /* the movement catalogue */ ],
  "cycles":         [ /* every cycle, each with its FULL plan */ ],
  "currentCycleId": "…",
  "activeWorkoutId": "…",
  "workouts":       [ /* every session, with planned and actual sets */ ]
}
```

Why each piece is there:

| Field | Why a restore needs it |
|---|---|
| `referenceMaxes` | The planning numbers onboarding would otherwise ask for again |
| `exercises` | Templates and logs reference movements by id |
| `cycles[].program` | The plan **as trained**, not a rule for regenerating it |
| `cycles[].program.currentWorkoutTemplateId` | Where the lifter is in the sequence |
| `cycles[].referenceMaxes` | The snapshot each block was calculated from, frozen |
| `workouts[]` | Full history, planned and actual, for statistics and the log |
| `activeWorkoutId` | The session to reopen, mid-set |
| `syncVersion` | The cursor a future delta sync resumes from |

`schemaVersion` describes this document, not the database. A client can refuse a version it does
not understand rather than restoring something it half recognises.

The endpoint is read-only and idempotent — asking twice changes nothing. It currently returns the
lifter's whole history; a `since` cursor built on `syncVersion` is Phase 2's job, so an
established account stops re-downloading years of data it already has.

---

## Errors

| Status | When |
|---|---|
| `400` | Validation failed. `errors` maps each field to its messages. |
| `401` | Missing, invalid, expired or revoked credentials. |
| `404` | The record does not exist — **or is not yours**. |
| `409` | Duplicate email, taken identifier, second active workout, stale `expectedVersion`. |
| `422` | Well-formed, but the rules forbid it: completing a cycle twice, writing into a finished session. |
| `429` | Rate limited. Honour `Retry-After`. |
| `500` | Something broke. The detail is in the server's log, not in the response. |

Validation errors name the field as the client sent it:

```json
{
  "type": "https://tools.ietf.org/html/rfc9110#section-15.5.1",
  "title": "Validation failed",
  "status": 400,
  "errors": {
    "email": ["'Email' is not a valid email address."],
    "program.weeks[0].workouts[0].plannedSets[1].rpeMin":
      ["'Rpe Min' must be between 1.0 and 10.0 in steps of 0.5."]
  }
}
```

---

## Rate limiting

Two buckets, partitioned per authenticated lifter where there is one and per address otherwise —
so a gym on shared Wi-Fi is not locked out by one bad client on the same network.

| Bucket | Default | Applies to |
|---|---|---|
| Authentication | 10/minute | `register`, `login`, `refresh`, `logout` |
| General | 300/minute | Everything |

A rejected request is `429` with `Retry-After` in seconds. There is no queue: making a failed
login wait achieves nothing, and refusing it outright is the signal a client should back off on.

---

## Assumptions a syncing client should hold

1. **The phone owns identity.** Generate GUIDs locally and send them. The server keeps them.
2. **Retry freely on creates.** They are idempotent by id.
3. **Send `expectedVersion` on writes** and use the `version` from the previous response.
4. **Persist a rotated refresh token immediately**, before doing anything else with the response.
5. **Treat `404` as "gone or not mine"**, never as "the endpoint is wrong".
6. **Records are never hard-deleted.** A future delta feed will report removals as tombstones, so
   do not assume absence means deletion.
7. **Weights and RPE are decimals.** Do not round-trip them through a float.
