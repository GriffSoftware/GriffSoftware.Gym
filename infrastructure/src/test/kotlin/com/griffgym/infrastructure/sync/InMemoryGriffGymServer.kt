package com.griffgym.infrastructure.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

/**
 * A Griff Gym API that actually remembers what it was sent.
 *
 * A dispatcher returning canned JSON would prove that the app can parse a document somebody
 * wrote by hand. What needs proving is different and much stronger: that what the app
 * *uploads* is exactly what it can later *restore* — that the two mappers are inverses over
 * real JSON, real HTTP and real serialisation.
 *
 * So this keeps the request bodies, reshapes them into the state document the way the real
 * server does, and serves them back. It is a stand-in for the backend's storage, not for its
 * behaviour: it is deliberately permissive, because the C# API has its own test suite and
 * duplicating those rules here would only mean two places to keep in step.
 */
internal class InMemoryGriffGymServer(private val json: Json) : Dispatcher() {

    private val exercises = linkedMapOf<String, JsonObject>()
    private val referenceMaxes = linkedMapOf<String, JsonObject>()
    private val cycles = linkedMapOf<String, JsonObject>()
    private val workouts = linkedMapOf<String, JsonObject>()

    var stateRequests: Int = 0
        private set

    /** Wipes the account, the way a brand new registration looks. */
    fun clear() {
        exercises.clear()
        referenceMaxes.clear()
        cycles.clear()
        workouts.clear()
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path.orEmpty().substringBefore('?')
        val body = request.body.readUtf8()

        return when {
            path == "/api/v1/state" -> {
                stateRequests += 1
                ok(state())
            }

            path.startsWith("/api/v1/reference-maxes/") && request.method == "PUT" ->
                ok(storeReferenceMax(path.substringAfterLast('/'), body))

            path == "/api/v1/cycles" && request.method == "POST" -> ok(storeCycle(body))

            path == "/api/v1/workouts" && request.method == "POST" -> ok(storeWorkout(body))

            path == "/api/v1/exercises" -> ok(JsonArray(exercises.values.toList()))

            else -> MockResponse().setResponseCode(404)
        }
    }

    // --- writes -------------------------------------------------------------------------------

    private fun storeReferenceMax(lift: String, body: String): JsonObject {
        val request = json.parseToJsonElement(body).jsonObject
        val id = request["id"]?.jsonPrimitive?.content ?: "max-$lift"

        val stored = buildJsonObject {
            put("id", id)
            put("lift", lift)
            put("valueKg", request.getValue("valueKg").jsonPrimitive.content.toDouble())
            put("createdAtUtc", CREATED_AT)
            put("updatedAtUtc", CREATED_AT)
            put("version", (referenceMaxes[id]?.get("version")?.jsonPrimitive?.int() ?: 0) + 1)
            put("syncVersion", nextSyncVersion())
        }

        referenceMaxes[id] = stored
        return stored
    }

    /**
     * Reshapes a create request into the response the server would return.
     *
     * The two shapes differ in exactly the ways that matter to a restore — the request's
     * optional ids are mandatory in the response, and the plan carries `isDeload` alongside its
     * type — so doing this properly is what makes the round trip a real test.
     */
    private fun storeCycle(body: String): JsonObject {
        val request = json.parseToJsonElement(body).jsonObject
        val id = request["id"]?.jsonPrimitive?.content ?: "cycle-${cycles.size + 1}"

        request["exercises"]?.jsonArray?.forEach { element ->
            val exercise = element.jsonObject
            val exerciseId = exercise.getValue("id").jsonPrimitive.content
            exercises[exerciseId] = buildJsonObject {
                put("id", exerciseId)
                put("name", exercise.getValue("name").jsonPrimitive.content)
                put("category", exercise.getValue("category").jsonPrimitive.content)
                put("createdAtUtc", CREATED_AT)
                put("updatedAtUtc", CREATED_AT)
                put("version", 1)
                put("syncVersion", nextSyncVersion())
            }
        }

        val program = request.getValue("program").jsonObject

        val stored = buildJsonObject {
            put("id", id)
            put("cycleNumber", request.getValue("cycleNumber").jsonPrimitive.int())
            put("status", "Active")
            put(
                "referenceMaxes",
                buildJsonObject {
                    put("squatKg", request.getValue("squatReferenceMaxKg").jsonPrimitive.double())
                    put(
                        "benchPressKg",
                        request.getValue("benchPressReferenceMaxKg").jsonPrimitive.double(),
                    )
                    put(
                        "deadliftKg",
                        request.getValue("deadliftReferenceMaxKg").jsonPrimitive.double(),
                    )
                },
            )
            put("startedAtUtc", request.getValue("startedAtUtc").jsonPrimitive.content)
            put("createdAtUtc", request.getValue("startedAtUtc").jsonPrimitive.content)
            put("updatedAtUtc", request.getValue("startedAtUtc").jsonPrimitive.content)
            put("version", 1)
            put("syncVersion", nextSyncVersion())
            put("program", program.toProgramResponse(id))
        }

        cycles[id] = stored
        return stored
    }

    private fun JsonObject.toProgramResponse(cycleId: String): JsonObject = buildJsonObject {
        put("id", this@toProgramResponse["id"]?.jsonPrimitive?.content ?: "program-$cycleId")
        put("name", getValue("name").jsonPrimitive.content)
        this@toProgramResponse["currentWorkoutTemplateId"]?.let { put("currentWorkoutTemplateId", it) }
        put(
            "weeks",
            buildJsonArray {
                getValue("weeks").jsonArray.forEachIndexed { weekIndex, weekElement ->
                    val week = weekElement.jsonObject
                    val type = week.getValue("type").jsonPrimitive.content

                    add(
                        buildJsonObject {
                            put("id", week.identified("week-$weekIndex"))
                            put("weekNumber", week.getValue("weekNumber").jsonPrimitive.int())
                            put("label", week.getValue("label").jsonPrimitive.content)
                            put("type", type)
                            // The response carries both; the request only the type. A restore
                            // that ignored one of them would silently lose the deload week.
                            put("isDeload", type == "Deload")
                            put(
                                "workouts",
                                buildJsonArray {
                                    week.getValue("workouts").jsonArray
                                        .forEachIndexed { dayIndex, workoutElement ->
                                            add(
                                                workoutElement.jsonObject
                                                    .toWorkoutTemplateResponse("$weekIndex-$dayIndex"),
                                            )
                                        }
                                },
                            )
                        },
                    )
                }
            },
        )
    }

    private fun JsonObject.toWorkoutTemplateResponse(fallback: String): JsonObject =
        buildJsonObject {
            put("id", identified("template-$fallback"))
            put("dayNumber", getValue("dayNumber").jsonPrimitive.int())
            put("sequenceNumber", getValue("sequenceNumber").jsonPrimitive.int())
            put("title", getValue("title").jsonPrimitive.content)
            put(
                "exercises",
                buildJsonArray {
                    getValue("exercises").jsonArray.forEachIndexed { index, element ->
                        val exercise = element.jsonObject
                        val exerciseId = exercise.getValue("exerciseId").jsonPrimitive.content

                        add(
                            buildJsonObject {
                                put("id", exercise.identified("exerciseTemplate-$fallback-$index"))
                                put("position", exercise.getValue("position").jsonPrimitive.int())
                                put("exerciseId", exerciseId)
                                put(
                                    "exerciseName",
                                    exercise["exerciseName"]?.jsonPrimitive?.content
                                        ?: exercises[exerciseId]
                                            ?.get("name")?.jsonPrimitive?.content
                                        ?: "Unknown exercise",
                                )
                                put(
                                    "exerciseCategory",
                                    exercise["exerciseCategory"]?.jsonPrimitive?.content
                                        ?: exercises[exerciseId]
                                            ?.get("category")?.jsonPrimitive?.content
                                        ?: "Accessory",
                                )
                                put("type", exercise.getValue("type").jsonPrimitive.content)
                                put(
                                    "plannedSets",
                                    buildJsonArray {
                                        exercise.getValue("plannedSets").jsonArray
                                            .forEachIndexed { setIndex, setElement ->
                                                val set = setElement.jsonObject
                                                add(
                                                    buildJsonObject {
                                                        put(
                                                            "id",
                                                            set.identified("set-$fallback-$index-$setIndex"),
                                                        )
                                                        copyAcross(
                                                            set,
                                                            "position",
                                                            "weightKg",
                                                            "reps",
                                                            "rpeMin",
                                                            "rpeMax",
                                                        )
                                                    },
                                                )
                                            }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }

    private fun storeWorkout(body: String): JsonObject {
        val request = json.parseToJsonElement(body).jsonObject
        val id = request["id"]?.jsonPrimitive?.content ?: "workout-${workouts.size + 1}"
        val status = request["status"]?.jsonPrimitive?.content ?: "InProgress"

        val stored = buildJsonObject {
            put("id", id)
            request["trainingCycleId"]?.let { put("trainingCycleId", it) }
            request["workoutTemplateId"]?.let { put("workoutTemplateId", it) }
            put("weekNumber", request["weekNumber"]?.jsonPrimitive?.int() ?: 1)
            put("dayNumber", request["dayNumber"]?.jsonPrimitive?.int() ?: 1)
            put("title", request["title"]?.jsonPrimitive?.content ?: "Workout")
            put("isDeload", request["isDeload"]?.jsonPrimitive?.content?.toBoolean() ?: false)
            put("status", status)
            put("performedOn", request.getValue("performedOn").jsonPrimitive.content)
            put("startedAtUtc", request.getValue("startedAtUtc").jsonPrimitive.content)
            request["finishedAtUtc"]?.let { put("finishedAtUtc", it) }
            put("totalVolumeKg", request.tonnage())
            put("totalSets", request.setCount())
            put("completedSets", request.completedSetCount())
            put("totalReps", 0)
            request["notes"]?.let { put("notes", it) }
            put("createdAtUtc", request.getValue("startedAtUtc").jsonPrimitive.content)
            put("updatedAtUtc", request.getValue("startedAtUtc").jsonPrimitive.content)
            put("version", (workouts[id]?.get("version")?.jsonPrimitive?.int() ?: 0) + 1)
            put("syncVersion", nextSyncVersion())
            put(
                "exercises",
                buildJsonArray {
                    request["exercises"]?.jsonArray?.forEachIndexed { index, element ->
                        add(element.jsonObject.toExerciseLogResponse("$id-$index"))
                    }
                },
            )
        }

        workouts[id] = stored
        return stored
    }

    private fun JsonObject.toExerciseLogResponse(fallback: String): JsonObject = buildJsonObject {
        val exerciseId = this@toExerciseLogResponse["exerciseId"]?.jsonPrimitive?.content

        put("id", identified("log-$fallback"))
        put("position", getValue("position").jsonPrimitive.int())
        exerciseId?.let { put("exerciseId", it) }
        put(
            "exerciseName",
            this@toExerciseLogResponse["exerciseName"]?.jsonPrimitive?.content
                ?: exercises[exerciseId]?.get("name")?.jsonPrimitive?.content
                ?: "Unknown exercise",
        )
        put(
            "exerciseCategory",
            this@toExerciseLogResponse["exerciseCategory"]?.jsonPrimitive?.content
                ?: exercises[exerciseId]?.get("category")?.jsonPrimitive?.content
                ?: "Accessory",
        )
        put("type", getValue("type").jsonPrimitive.content)
        put("volumeKg", 0.0)
        put(
            "sets",
            buildJsonArray {
                getValue("sets").jsonArray.forEachIndexed { index, element ->
                    val set = element.jsonObject
                    add(
                        buildJsonObject {
                            put("id", set.identified("setLog-$fallback-$index"))
                            copyAcross(
                                set,
                                "position",
                                "plannedWeightKg",
                                "plannedReps",
                                "plannedRpeMin",
                                "plannedRpeMax",
                                "actualWeightKg",
                                "actualReps",
                                "actualRpe",
                                "notes",
                            )
                            put(
                                "completed",
                                set["completed"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                            )
                            put("volumeKg", 0.0)
                        },
                    )
                }
            },
        )
    }

    // --- reads --------------------------------------------------------------------------------

    private fun state(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("generatedAtUtc", CREATED_AT)
        put("syncVersion", syncVersion)
        put(
            "profile",
            buildJsonObject {
                put("id", "user-1")
                put("email", "lifter@example.com")
                put("createdAtUtc", CREATED_AT)
                put("updatedAtUtc", CREATED_AT)
            },
        )
        put("referenceMaxes", JsonArray(referenceMaxes.values.toList()))
        put("exercises", JsonArray(exercises.values.toList()))
        put("cycles", JsonArray(cycles.values.toList()))
        cycles.values.lastOrNull()?.let { put("currentCycleId", it.getValue("id")) }
        workouts.values
            .firstOrNull { it.getValue("status").jsonPrimitive.content == "InProgress" }
            ?.let { put("activeWorkoutId", it.getValue("id")) }
        put("workouts", JsonArray(workouts.values.toList()))
    }

    // --- helpers ------------------------------------------------------------------------------

    private var syncVersion: Long = 0

    private fun nextSyncVersion(): Long = ++syncVersion

    private fun ok(payload: JsonElement) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(payload.toString())

    private fun JsonObject.identified(fallback: String): String =
        this["id"]?.jsonPrimitive?.content ?: fallback

    private fun JsonObjectBuilderScope.copyAcross(source: JsonObject, vararg keys: String) {
        keys.forEach { key -> source[key]?.let { if (it !is JsonNull) put(key, it) } }
    }

    private fun JsonObject.tonnage(): Double =
        this["exercises"]?.jsonArray.orEmpty().sumOf { exercise ->
            exercise.jsonObject.getValue("sets").jsonArray.sumOf { set ->
                val fields = set.jsonObject
                val completed = fields["completed"]?.jsonPrimitive?.content?.toBoolean() ?: false
                val weight = fields["actualWeightKg"]?.jsonPrimitive?.double() ?: 0.0
                val reps = fields["actualReps"]?.jsonPrimitive?.int() ?: 0
                if (completed) weight * reps else 0.0
            }
        }

    private fun JsonObject.setCount(): Int =
        this["exercises"]?.jsonArray.orEmpty()
            .sumOf { it.jsonObject.getValue("sets").jsonArray.size }

    private fun JsonObject.completedSetCount(): Int =
        this["exercises"]?.jsonArray.orEmpty().sumOf { exercise ->
            exercise.jsonObject.getValue("sets").jsonArray.count {
                it.jsonObject["completed"]?.jsonPrimitive?.content?.toBoolean() == true
            }
        }

    private companion object {
        const val CREATED_AT = "2026-03-04T09:30:00Z"
    }
}

private typealias JsonObjectBuilderScope = kotlinx.serialization.json.JsonObjectBuilder

private typealias JsonNull = kotlinx.serialization.json.JsonNull

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

private fun JsonPrimitive.int(): Int = content.toDouble().toInt()

private fun JsonPrimitive.double(): Double = content.toDouble()
